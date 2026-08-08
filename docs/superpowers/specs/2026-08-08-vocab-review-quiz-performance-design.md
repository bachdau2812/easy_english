# Vocab Review Quiz Performance and Distribution Design

## Status

Approved on 2026-08-08.

## Context

The vocab review APIs currently expose two unchanged frontend flows:

- `GET /exercises/vocab-review` returns a batch of review quizzes.
- `GET /exercises/vocab-review/word` returns at most one additional quiz for a saved vocabulary, normally after a wrong answer.

The current implementation can spend most of its time in two places. First, the request synchronously calls Groq when a standard sense has fewer than four examples. Second, each generated quiz rebuilds the full vocabulary context and repeatedly loads words, meanings, sounds, and examples. For a batch of `N` vocabularies this makes the static-data work approach `O(N^2)`. The current exercise-progress check also performs six to eight sequential Redis `hasKey` calls per word.

The existing final-quiz cache is unsafe as a shared cache. Its key is based on sense and exercise type, while its value contains `userVocabId` and localized fields. The key does not include the user or language, so the cached response can carry data from a different request.

This design improves latency without changing any endpoint, request parameter, response DTO, or frontend orchestration. Groq example generation remains synchronous.

## Goals

- Reduce repeated database and Redis I/O during quiz generation.
- Make static-data loading for a batch approach `O(N)` instead of `O(N^2)`.
- Preserve synchronous Groq generation for missing examples.
- Keep the existing API contract and `VocabReviewQuizResponse[]` shape.
- Balance exercise types across the initial batch as evenly as available data permits.
- Avoid repeating an exercise type for the same user and word during its two-hour review window.
- Keep Redis an optional acceleration layer; the database remains the source of truth.
- Prevent user-specific data from entering shared dictionary caches.
- Provide enough metrics to compare behavior and latency before and after rollout.

## Non-goals

- Moving Groq generation to a background queue.
- Adding a review session identifier to the API.
- Changing spaced-repetition scheduling or attempt submission behavior.
- Guaranteeing a fixed latency SLA.
- Caching the final `VocabReviewQuizResponse`.
- Reworking unrelated dictionary or learning-resource APIs.

## Selected Approach

Use a staged hybrid design:

1. Select review vocabularies as today.
2. Run synchronous example preflight once for the deduplicated selected senses.
3. Bulk-load immutable quiz data into a request-scoped context.
4. Cache shared vocabulary snapshots in Redis using versioned keys.
5. Build all final quizzes in memory from the request context.
6. Use a balanced scheduler to assign exercise types across a batch.
7. Replace per-type Redis keys with an atomic, per-user/per-word progress sorted set.

The implementation must first deliver request-scoped bulk loading and only then add Redis snapshot caching. This makes each performance improvement independently testable and reversible.

## Component Boundaries

### `ReviewVocabSelector`

Selects due `UserVocabulary` records and applies existing level quotas. It does not load dictionary data or generate quiz content.

### `ReviewExamplePreflightService`

Receives the selected vocabularies, deduplicates standard senses by `wordId + senseId`, batch-loads current example counts, calls Groq once for the missing inputs, persists accepted examples, and invalidates affected snapshot revisions after commit.

The service keeps the current behavior of swallowing an expected example-generation `AppException` so that review can proceed with exercise types whose data already exists.

### `ReviewVocabDataLoader`

Resolves shared static data from Redis and the database. It returns one snapshot per requested sense and language. It must use batch Redis and database operations rather than per-vocabulary calls.

### `ReviewRequestContext`

Holds all request-scoped user vocabulary mappings, shared snapshots, and precomputed distractor pools. It is immutable after construction and reused by every quiz generated in the request.

### `BalancedReviewQuizScheduler`

Builds the eligibility matrix and assigns at most one exercise type to each target vocabulary. It balances types across the batch while excluding unavailable and recently used types.

### `ReviewQuizFactory`

Builds a `VocabReviewQuizResponse` entirely in memory. It receives a target `UserVocabulary`, its snapshot, the request context, and the assigned exercise type. It must not call a repository, Redis, Groq, or any other remote service.

### `ReviewProgressStore`

Reads and atomically reserves recently used exercise types in Redis. It hides the sorted-set and Lua implementation from the service and exposes a small domain-oriented interface.

### `ReviewSnapshotCache`

Owns snapshot serialization, revision reads, batch cache reads/writes, corruption handling, and cache bypass behavior. Cache failures never make the review API fail.

## Data Models

Shared cache values use a DTO that contains no user-specific fields:

```java
ReviewVocabSnapshot {
    int schemaVersion;
    String wordId;
    String senseKey;
    String langCode;
    String word;
    String pos;
    String meaning;
    WordSenseResponse wordSense;
    List<WordSoundResponse> sounds;
    List<ReviewExample> examples;
    Instant generatedAt;
}
```

It must not contain `userId`, `userVocabId`, level, correct turns, or next-review time.

The request-scoped model associates user state with shared snapshots:

```java
ReviewRequestContext {
    Map<String, UserVocabulary> userVocabById;
    Map<String, ReviewVocabSnapshot> snapshotByUserVocabId;
    List<String> meaningDistractors;
    List<String> wordDistractors;
    List<String> soundDistractors;
}
```

Distractor lists are distinct, shuffled once per request, and filtered for playable or nonblank values as appropriate.

## Batch Data Loading

The loader must avoid one large join across all one-to-many tables because combining sounds and examples would multiply rows. Cache misses are loaded through at most three logical query groups:

1. Words, selected senses, and localized meanings.
2. Playable sounds for all requested word IDs.
3. Examples and translations for all requested sense keys and the requested language.

The loader assembles snapshots in memory. IDs are deduplicated before each query. The normal batch is at most 90 vocabularies, so a single `IN` query per group is acceptable; repository methods may chunk defensively if database limits require it.

For `GET /vocab-review/word`, the loader uses the target plus at most 32 due vocabularies as distractor candidates. This bounds single-word work even when a user has thousands of due vocabularies. If the bounded pool cannot support a particular exercise type, the scheduler selects another eligible type.

## Synchronous Example Preflight

The preflight remains in the request critical path and runs before snapshot loading so newly persisted examples can be included in the same response.

The preflight performs these steps:

1. Filter to standard senses and deduplicate by `wordId + senseId`.
2. Batch-load words, senses, and existing example counts.
3. Build only inputs whose sense has fewer than four usable examples.
4. Acquire a distributed lock per missing sense before invoking Groq.
5. Recheck the database after lock acquisition to avoid duplicate work.
6. Send the deduplicated missing inputs to Groq synchronously.
7. Validate and persist accepted examples and localizations using `saveAll` and JDBC batching.
8. Publish changed word IDs and increment cache revisions after transaction commit.

The lock key is:

```text
lock:review_example_generation:v1:{wordId}:{senseId}
```

Its TTL is 120 seconds, exceeding the 90-second Groq request timeout. A request that does not acquire the lock periodically rechecks for the generated examples within the same bounded wait. If generation fails or the wait expires, the request continues with currently available quiz data.

Groq requests are not parallelized in the initial implementation. Rate-limit-aware chunking or bounded concurrency can be considered only after metrics show that Groq remains the dominant successful-request latency.

## Snapshot Cache

### Keys

Each word has a persistent revision key:

```text
review_vocab_revision:v1:{wordId}
```

Snapshot keys include the current revision:

```text
review_vocab_snapshot:v1:{wordId}:{senseKey}:{langCode}:{revision}
```

`senseKey` is namespaced as either `sense:{senseId}` or `localized:{senseLocalizedId}`.

### Read flow

1. Deduplicate requested words and snapshots.
2. `MGET` all word revision keys, treating a missing revision as zero.
3. Build snapshot keys with the resolved revisions.
4. `MGET` all snapshot keys.
5. Validate schema, identity, and language of each cached value.
6. Bulk-load all misses from the database.
7. Pipeline writes for newly built snapshots.

This limits primary Redis round trips for the batch to a small constant rather than one call per word.

### TTL

- Initial snapshot TTL: six hours plus random jitter from zero to 30 minutes.
- Revision keys: no expiration.
- After all application write paths reliably publish invalidations, snapshot TTL may be raised to 24 hours plus jitter.

Jitter prevents a large set of related keys from expiring simultaneously.

### Invalidation

Any committed change to a word, sense, sense localization, sound, example, or example localization increments the affected word revision:

```text
INCR review_vocab_revision:v1:{wordId}
```

Writers publish `ReviewVocabDataChangedEvent(wordId)`. A listener handles the event with `@TransactionalEventListener(phase = AFTER_COMMIT)`, deduplicates word IDs, and increments each revision once. Old snapshot keys become unreachable and expire naturally, avoiding wildcard deletion and Redis `SCAN`.

Required publishers include generated-example persistence, word-info insertion, and localization persistence. Direct SQL imports bypass application invalidation, so the finite TTL remains a safety net and an administrative revision-bump operation must be available for import workflows.

### Cache stampede behavior

Request-local deduplication is mandatory. Cross-instance snapshot locks are optional but recommended:

```text
lock:review_vocab_snapshot:v1:{snapshotKey}
```

The lock TTL is five to ten seconds. A contender retries the cache once after a short wait, then falls back to the same bulk database load. Snapshot locks must never hold the user request for a long period.

### Final quiz cache retirement

The application stops reading and writing `review_quiz:v2:*`. Existing keys expire under their current two-hour TTL. Final response caching is not replaced because quiz assembly is inexpensive, distractors are request-specific, and final responses contain user-specific IDs.

## Exercise Eligibility

The scheduler calculates eligibility from snapshots and distractor pools before assigning types:

- `VOCAB_WORD_TO_MEANING`: target has a nonblank meaning.
- `VOCAB_FILL_MISSING_WORD_PART`: normalized word has more than two letters.
- `VOCAB_LISTEN_AND_TYPE_WORD`: target has a playable sound.
- `VOCAB_CHOOSE_WORD_IN_SENTENCE_BLANK`: target has a usable example.
- `VOCAB_FILL_WORD_IN_SENTENCE_BLANK`: target has a usable example and more than two normalized letters.
- `VOCAB_MEANING_TO_SOUND`: target has a playable sound and at least three distinct playable sound distractors.
- `VOCAB_SENTENCE_TO_MEANING`: target has a usable example, a meaning, and at least three distinct meaning distractors.
- `VOCAB_SENTENCE_BLANK_TO_SOUND`: target has a usable example, a playable sound, and at least three distinct playable sound distractors.

Recently used types from `ReviewProgressStore` are removed from eligibility. This prevalidation avoids choosing types that are known to fail.

## Balanced Batch Scheduling

For `N` selected targets and eight exercise types:

```text
baseQuota = N / 8
remainder = N % 8
```

Each type receives `baseQuota`; `remainder` randomly ordered types receive one additional desired slot. Injecting `RandomGenerator` keeps production behavior random and tests deterministic.

Scheduling is best-effort because not every word supports every type:

1. Sort targets by ascending number of eligible types so constrained words are assigned first.
2. For a target, choose the eligible type with the largest `desiredQuota - assignedCount` deficit.
3. Break a deficit tie by choosing the type with the lower assigned count.
4. Break remaining ties through the injected random generator.
5. If all eligible types have reached desired quota, choose the eligible type with the lowest assigned count.
6. If no eligible type remains, omit that target.

When data supports all types, the acceptance invariant is:

```text
max(typeCount) - min(typeCount) <= 1
```

When a type lacks enough eligible targets, its unused quota is redistributed to the least-used eligible types. The system prioritizes returning the requested number of quizzes over enforcing an impossible hard quota.

`GET /vocab-review/word` has only one target and therefore does not apply a batch quota. It excludes types already used for the word and randomly chooses among the remaining eligible types.

## Review Progress Store

Replace individual current-review keys with one sorted set per user and word:

```text
review_progress:v2:{userId}:{wordId}
```

Members are exercise type names. Scores are their expiration timestamps in epoch milliseconds. A Lua script atomically:

1. Removes expired members.
2. Receives Java's eligible candidate list in preferred order.
3. Selects the first candidate that is not present.
4. Adds it with `now + two hours` as the score.
5. Sets a cleanup TTL on the sorted-set key.
6. Returns the reserved type.

This preserves a separate two-hour window per type while reducing six to eight sequential `hasKey` calls plus a write to one round trip. Batch reservations are pipelined. Reservation conflicts caused by concurrent requests are rescheduled for affected targets.

An expected content failure keeps the type reserved, matching current skip behavior. An unexpected infrastructure failure removes the reservation with `ZREM` so the type can be retried later.

During deployment, legacy current-review keys may be batch-read and used to seed the new progress sets for one two-hour transition window. The compatibility path is then removed. The existing wrong-attempt marker is unrelated and remains unchanged.

## Request Flows

### Initial batch

1. Validate user and requested total.
2. Select due vocabularies with existing quota rules.
3. Run synchronous, deduplicated example preflight.
4. Resolve snapshot cache hits and bulk-load misses.
5. Build one immutable request context and distractor pools.
6. Batch-read current progress.
7. Calculate eligibility and balanced assignments.
8. Atomically reserve assigned types, rescheduling conflicts.
9. Build final quizzes in memory.
10. Redis-write any new snapshots and return the unchanged response DTO list.

### Additional quiz for a wrong word

1. Validate user, target, and ownership.
2. Run synchronous example preflight for the target.
3. Load the target and at most 32 due distractor candidates.
4. Resolve snapshots and build one request context.
5. Calculate remaining eligible types for the target.
6. Atomically reserve one type.
7. Build and return one quiz, or return an empty list when no type remains.

## Failure Handling

- Redis revision or snapshot read failure: bypass cache and bulk-load the database.
- Redis cache write failure: log and return the generated quiz.
- Corrupt or mismatched snapshot: count it as invalid, ignore it, and reload from the database.
- Redis progress failure: use an in-memory best-effort selection and emit a warning metric; do not fail the API solely because progress storage is unavailable.
- Groq timeout or expected generation error: continue with existing data and eligible non-example types.
- Expected quiz content failure after scheduling: mark the type unavailable for that target and reschedule another type.
- Unexpected database or system failure: release any new progress reservation and use the existing global exception flow.
- No eligible type for a target: omit the target. Returning fewer quizzes remains valid behavior.

The database is always the source of truth. Cache failures must not alter ownership validation or expose another user's identifiers.

## Observability

Add timers for total review, selection, example preflight, Groq, snapshot cache read, snapshot database load, progress read/reservation, scheduling, and in-memory quiz building.

Add counters for snapshot hits, misses, invalid values, generated and skipped quizzes, Redis fallbacks, generated examples, reservation conflicts, and generated counts per exercise type.

The request-completion log includes requested, selected, and generated counts; cache hits and misses; Groq input count and duration; database load duration; quiz-build duration; and exercise-type distribution. It must not log JWTs, answers, private content, or generated challenge solutions.

Performance comparison uses p50, p95, and p99 timers plus bounded repository-invocation assertions. No fixed user-facing SLA is introduced.

## Testing Strategy

### Unit tests

- Balanced distribution differs by at most one when all types are eligible.
- Missing sound or example capacity is redistributed to eligible types.
- Constrained targets are scheduled before flexible targets.
- Recently used types are never assigned to the same user and word.
- Deterministic injected randomness makes remainder allocation testable.
- `ReviewQuizFactory` performs no repository or Redis calls.
- Snapshot values contain no user-specific fields.
- Snapshot keys distinguish standard/localized senses, languages, and revisions.
- Eligibility rules match the data requirements of all eight exercise types.

### Integration tests

- Cache miss bulk-loads data and writes valid snapshots.
- Cache hit avoids static-data repository queries.
- Revision changes make old snapshots unreachable.
- Revision increments happen only after a successful transaction commit.
- Redis failure falls back to database data without changing the response contract.
- Concurrent reservations cannot return the same type for the same user and word.
- Concurrent preflights do not invoke Groq twice for the same sense.
- Batch and single-word endpoints retain their existing request and response contracts.
- The single-word endpoint returns either a one-item list or an empty list.

### Performance regression tests

- Repository invocation growth for batches 30, 60, and 90 is bounded and not quadratic.
- Building 90 quizzes from an existing request context performs no I/O.
- Progress reads and reservations use pipeline/Lua operations rather than per-type `hasKey` calls.
- A warm snapshot cache performs no static-data DB load after vocabulary selection and preflight checks.

Tests remain deterministic and do not use real Groq, SMTP, Firebase, Azure, or production data.

## Rollout Plan

1. Correct the existing `taken++F` compilation error before performance work.
2. Add timing and distribution metrics to establish a baseline.
3. Extract the pure `ReviewQuizFactory` and request-scoped context.
4. Add bulk repositories and the non-cached data loader.
5. Add eligibility calculation and the balanced scheduler.
6. Replace progress markers with sorted-set/Lua storage and a legacy transition read.
7. Add revisioned snapshot caching and after-commit invalidation.
8. Optimize example persistence batching and add the Groq single-flight lock.
9. Compare metrics and repository invocation counts with the baseline.
10. Raise snapshot TTL from six to 24 hours only after invalidation coverage is proven.

Use independent feature flags:

```properties
review.balanced-scheduler.enabled=true
review.snapshot-cache.enabled=true
```

Flags permit targeted rollback without reverting the API or the bulk-loader refactor.

## Acceptance Criteria

- Frontend endpoints, parameters, and response DTOs are unchanged.
- Groq generation remains synchronous.
- Static quiz data is loaded once per unique snapshot per request.
- Warm snapshot reads do not query static dictionary data.
- Final quiz responses are not stored in a shared cache.
- Shared cache values contain no `userId` or `userVocabId`.
- Exercise type counts differ by at most one when all types are eligible.
- Unavailable type quota is redistributed to eligible types.
- A user and word do not repeat a reserved type during its two-hour window.
- Cache and progress Redis failures have defined fallbacks.
- Data changes invalidate snapshots after commit through revision increments.
- Batch repository work no longer grows quadratically with quiz count.
- Existing review attempt and spaced-repetition behavior remains unchanged.
