# Vocab Review Quiz Performance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reduce vocab-review latency with request-scoped bulk data, revisioned Redis snapshots, atomic progress tracking, and balanced exercise-type scheduling without changing the frontend API contract.

**Architecture:** The implementation separates review selection, static snapshot loading, progress reservation, balanced scheduling, and in-memory quiz construction. Groq remains synchronous, Redis remains optional, and the database remains the source of truth.

**Tech Stack:** Java 21, Spring Boot, Spring Data JPA, Spring Data Redis, Micrometer, Jackson, JUnit 5, Mockito, AssertJ, Maven Wrapper.

## Global Constraints

- Preserve `GET /exercises/vocab-review`, `GET /exercises/vocab-review/word`, and `VocabReviewQuizResponse[]` exactly.
- Keep Groq example generation synchronous.
- Never cache `userId`, `userVocabId`, review level, correct turns, or `nextReviewAt` in shared snapshot values.
- Redis failure must fall back to database/in-memory behavior rather than fail the review endpoint.
- Balance eight vocab exercise types across the initial batch; redistribute impossible quota to eligible types.
- Prevent repeated type reservation for the same user and word for two hours while Redis progress is available.
- Follow TDD for each behavior and do not use real Groq, Redis, MySQL production data, SMTP, Firebase, or Azure in unit tests.
- Preserve unrelated dirty-worktree changes and stage/commit only files or hunks belonging to this feature.

---

### Task 1: Restore a Compilable Baseline and Capture Existing Contract

**Files:**
- Modify: `src/main/java/com/bachdauduc/vocab_app/service/ExerciseService.java:650`
- Modify: `src/test/java/com/bachdauduc/vocab_app/service/ExerciseServiceTest.java`

**Interfaces:**
- Consumes: existing `ExerciseService#getReviewVocabs` and `#getReviewVocab`.
- Produces: a compiling baseline with endpoint-contract tests for later refactoring.

- [ ] **Step 1: Add tests that preserve list cardinality and single-word ownership behavior**

Add tests that call the public service methods and assert that batch generation never exceeds the requested total and that a target owned by another user throws `USER_VOCABULARY_NOT_FOUND`.

- [ ] **Step 2: Run the focused test and record the RED failure**

Run:

```powershell
.\mvnw.cmd -Dtest=ExerciseServiceTest test
```

Expected: compilation fails at `taken++F` before tests execute.

- [ ] **Step 3: Restore the intended increment**

```java
if (selectedIds.add(userVocabulary.getId())) {
    selected.add(userVocabulary);
    taken++;
}
```

- [ ] **Step 4: Run the focused test and full baseline**

```powershell
.\mvnw.cmd -Dtest=ExerciseServiceTest test
.\mvnw.cmd test
```

Expected: focused tests pass; document any pre-existing full-suite environmental failure before continuing.

---

### Task 2: Add the Balanced Exercise Scheduler

**Files:**
- Create: `src/main/java/com/bachdauduc/vocab_app/service/review/BalancedReviewQuizScheduler.java`
- Create: `src/main/java/com/bachdauduc/vocab_app/service/review/ReviewTargetEligibility.java`
- Create: `src/test/java/com/bachdauduc/vocab_app/service/review/BalancedReviewQuizSchedulerTest.java`

**Interfaces:**
- Consumes: `ExerciseType` and target eligibility sets.
- Produces: `Map<String, ExerciseType> schedule(List<ReviewTargetEligibility> targets)`.

- [ ] **Step 1: Write failing scheduler tests**

Cover these exact behaviors:

```java
assertThat(typeCounts(schedule(allEligibleTargets(30))).values()
        .allSatisfy(count -> assertThat(count).isBetween(3L, 4L));

assertThat(schedule(List.of(
        target("sound-only", VOCAB_LISTEN_AND_TYPE_WORD),
        target("flexible", VOCAB_LISTEN_AND_TYPE_WORD, VOCAB_WORD_TO_MEANING)
))).containsEntry("sound-only", VOCAB_LISTEN_AND_TYPE_WORD)
   .containsEntry("flexible", VOCAB_WORD_TO_MEANING);

assertThat(schedule(targetsWithoutSoundTypes()))
        .doesNotContainValue(VOCAB_MEANING_TO_SOUND)
        .doesNotContainValue(VOCAB_SENTENCE_BLANK_TO_SOUND);
```

Also cover deterministic remainder allocation through an injected `RandomGenerator` and omission of a target with no eligible types.

- [ ] **Step 2: Run tests to verify RED**

```powershell
.\mvnw.cmd -Dtest=BalancedReviewQuizSchedulerTest test
```

Expected: compilation fails because the scheduler types do not exist.

- [ ] **Step 3: Implement constrained-first balanced scheduling**

Use this public contract:

```java
public record ReviewTargetEligibility(
        String userVocabId,
        Set<ExerciseType> eligibleTypes
) {}

public Map<String, ExerciseType> schedule(List<ReviewTargetEligibility> targets)
```

The implementation computes floor/remainder quotas, shuffles only tie order, sorts targets by eligible count, selects the largest quota deficit, and falls back to the least-assigned eligible type.

- [ ] **Step 4: Run scheduler tests GREEN**

```powershell
.\mvnw.cmd -Dtest=BalancedReviewQuizSchedulerTest test
```

Expected: all scheduler tests pass.

---

### Task 3: Introduce Shared Snapshot and Request Context Models

**Files:**
- Create: `src/main/java/com/bachdauduc/vocab_app/service/review/ReviewExample.java`
- Create: `src/main/java/com/bachdauduc/vocab_app/service/review/ReviewVocabSnapshot.java`
- Create: `src/main/java/com/bachdauduc/vocab_app/service/review/ReviewRequestContext.java`
- Create: `src/test/java/com/bachdauduc/vocab_app/service/review/ReviewRequestContextTest.java`

**Interfaces:**
- Consumes: `UserVocabulary`, `WordSenseResponse`, and `WordSoundResponse`.
- Produces: immutable snapshot and context lookup APIs used by loader, scheduler, and factory.

- [ ] **Step 1: Write failing model/context tests**

Assert that context lookup preserves input order, distractor pools are distinct, and Jackson serialization of `ReviewVocabSnapshot` contains no `userId`, `userVocabId`, `level`, `currentLevelCorrectTurns`, or `nextReviewAt` fields.

- [ ] **Step 2: Run tests to verify RED**

```powershell
.\mvnw.cmd -Dtest=ReviewRequestContextTest test
```

Expected: compilation fails because the records do not exist.

- [ ] **Step 3: Implement immutable records and context factory**

Use these core shapes:

```java
public record ReviewExample(String id, String sentence, String translation) {}

public record ReviewVocabSnapshot(
        int schemaVersion,
        String wordId,
        String senseKey,
        String langCode,
        String word,
        String pos,
        String meaning,
        WordSenseResponse wordSense,
        List<WordSoundResponse> sounds,
        List<ReviewExample> examples,
        Instant generatedAt
) {}
```

`ReviewRequestContext` owns unmodifiable maps keyed by `userVocabId` and precomputes word, meaning, and playable-sound distractor lists once.

- [ ] **Step 4: Run context tests GREEN**

```powershell
.\mvnw.cmd -Dtest=ReviewRequestContextTest test
```

Expected: all context tests pass.

---

### Task 4: Add Revisioned Snapshot Cache and Bulk Data Loader

**Files:**
- Create: `src/main/java/com/bachdauduc/vocab_app/service/review/ReviewSnapshotCache.java`
- Create: `src/main/java/com/bachdauduc/vocab_app/service/review/ReviewVocabDataLoader.java`
- Create: `src/test/java/com/bachdauduc/vocab_app/service/review/ReviewSnapshotCacheTest.java`
- Create: `src/test/java/com/bachdauduc/vocab_app/service/review/ReviewVocabDataLoaderTest.java`
- Modify: `src/main/java/com/bachdauduc/vocab_app/repository/WordSoundRepository.java`
- Modify: `src/main/java/com/bachdauduc/vocab_app/repository/WordSenseLocalizationRepository.java`
- Modify: `src/main/java/com/bachdauduc/vocab_app/repository/WordExampleRepository.java`
- Modify: `src/main/java/com/bachdauduc/vocab_app/properties/RedisKeyProperties.java`
- Modify: `src/main/resources/redis_keys.properties`

**Interfaces:**
- Consumes: a deduplicated list of `UserVocabulary` and `langCode`.
- Produces: `Map<String, ReviewVocabSnapshot> load(List<UserVocabulary>, String)` keyed by user-vocab ID.

- [ ] **Step 1: Write failing cache tests**

Cover key identity, language and revision separation, batch hit/miss behavior, corrupt JSON fallback, Redis exception fallback, and TTL range from six hours through six hours thirty minutes.

- [ ] **Step 2: Write failing loader tests**

Assert that one load of 30/60/90 vocabularies invokes each bulk repository method at most once, loads only cache misses, and maps standard and localized senses to the correct snapshots.

- [ ] **Step 3: Run tests to verify RED**

```powershell
.\mvnw.cmd -Dtest=ReviewSnapshotCacheTest,ReviewVocabDataLoaderTest test
```

Expected: compilation fails because cache/loader classes and bulk repository methods do not exist.

- [ ] **Step 4: Add Redis properties**

Add:

```properties
redis.key.review-vocab-revision=review_vocab_revision:v1:%s
redis.key.review-vocab-snapshot=review_vocab_snapshot:v1:%s:%s:%s:%s
redis.ttl.review-vocab-snapshot-hours=6
redis.ttl.review-vocab-snapshot-jitter-minutes=30
```

Expose typed key and duration methods in `RedisKeyProperties`.

- [ ] **Step 5: Add bulk repository methods**

Add collection-based methods for sounds, localizations, and examples. Use separate query groups to avoid the sound/example Cartesian product. Preserve the existing single-word methods for unrelated callers.

- [ ] **Step 6: Implement cache-aside batch loading**

Read revisions and snapshots in Redis batch operations, validate deserialized identity/schema, bulk-load misses, and pipeline cache writes. Catch Redis runtime failures and use DB results.

- [ ] **Step 7: Run cache/loader tests GREEN**

```powershell
.\mvnw.cmd -Dtest=ReviewSnapshotCacheTest,ReviewVocabDataLoaderTest test
```

Expected: all cache and loader tests pass.

---

### Task 5: Add Atomic Review Progress Tracking

**Files:**
- Create: `src/main/java/com/bachdauduc/vocab_app/service/review/ReviewProgressStore.java`
- Create: `src/test/java/com/bachdauduc/vocab_app/service/review/ReviewProgressStoreTest.java`
- Modify: `src/main/java/com/bachdauduc/vocab_app/properties/RedisKeyProperties.java`
- Modify: `src/main/resources/redis_keys.properties`

**Interfaces:**
- Consumes: user ID, word ID, ordered eligible exercise types, and current time.
- Produces: `Optional<ExerciseType> reserveFirstAvailable(...)` and batch used-type reads.

- [ ] **Step 1: Write failing progress-store tests**

Verify key format, sorted-set expiry score, exclusion of unexpired members, cleanup of expired members, one-script reservation behavior, release on unexpected failure, and empty result when all candidates are reserved.

- [ ] **Step 2: Run tests to verify RED**

```powershell
.\mvnw.cmd -Dtest=ReviewProgressStoreTest test
```

Expected: compilation fails because `ReviewProgressStore` does not exist.

- [ ] **Step 3: Add progress Redis configuration**

```properties
redis.key.review-progress=review_progress:v2:%s:%s
redis.ttl.review-progress-hours=3
```

The three-hour key TTL is cleanup only; each member's score enforces its exact two-hour availability window.

- [ ] **Step 4: Implement Lua-backed reservation**

Use a `DefaultRedisScript<String>` that removes expired scores, reserves the first absent candidate with `ZADD`, sets cleanup expiry, and returns the selected member. Redis exceptions return an in-memory best-effort choice and emit a warning.

- [ ] **Step 5: Run progress-store tests GREEN**

```powershell
.\mvnw.cmd -Dtest=ReviewProgressStoreTest test
```

Expected: all progress-store tests pass.

---

### Task 6: Extract the In-Memory Quiz Factory

**Files:**
- Create: `src/main/java/com/bachdauduc/vocab_app/service/review/ReviewQuizFactory.java`
- Create: `src/test/java/com/bachdauduc/vocab_app/service/review/ReviewQuizFactoryTest.java`
- Modify: `src/main/java/com/bachdauduc/vocab_app/service/ExerciseService.java`

**Interfaces:**
- Consumes: target `UserVocabulary`, target snapshot, request context, exercise type.
- Produces: `VocabReviewQuizResponse create(UserVocabulary, ReviewVocabSnapshot, ReviewRequestContext, ExerciseType)`.

- [ ] **Step 1: Write one failing test per exercise type**

Assert correct answers and type-specific fields for all eight types, including four-option sound/meaning metadata and missing-letter behavior by level. Assert the factory constructor has no repository, Redis, HTTP, or Groq dependency.

- [ ] **Step 2: Run tests to verify RED**

```powershell
.\mvnw.cmd -Dtest=ReviewQuizFactoryTest test
```

Expected: compilation fails because the factory does not exist.

- [ ] **Step 3: Move pure generation logic into the factory**

Move masking, sentence replacement/underline, indexed-option, random-example, and response-building logic from `ExerciseService`. Replace repository-backed resolution with snapshot/context reads. Keep existing `VocabReviewQuizResponse` fields unchanged.

- [ ] **Step 4: Run factory tests GREEN**

```powershell
.\mvnw.cmd -Dtest=ReviewQuizFactoryTest test
```

Expected: all eight type tests pass without mocked I/O collaborators.

---

### Task 7: Integrate the Optimized Batch and Single-Word Flows

**Files:**
- Modify: `src/main/java/com/bachdauduc/vocab_app/service/ExerciseService.java`
- Modify: `src/main/java/com/bachdauduc/vocab_app/repository/UserVocabularyRepository.java`
- Modify: `src/test/java/com/bachdauduc/vocab_app/service/ExerciseServiceTest.java`
- Create: `src/test/java/com/bachdauduc/vocab_app/service/ExerciseServiceReviewFlowTest.java`
- Modify: `src/main/resources/application.properties`

**Interfaces:**
- Consumes: selector behavior, preflight, loader, scheduler, progress store, and factory.
- Produces: unchanged public `getReviewVocabs` and `getReviewVocab` behavior with bounded I/O.

- [ ] **Step 1: Write failing batch-flow tests**

Assert preflight occurs once, loader occurs once, distribution is balanced when all types are eligible, impossible quota is redistributed, final quiz count does not exceed request, and final response IDs belong to the current user's selected vocabularies.

- [ ] **Step 2: Write failing single-word tests**

Assert ownership validation, target plus at most 32 distractors, no repeated reserved type, one-item-or-empty response, and no shared final-response cache read/write.

- [ ] **Step 3: Run integration-focused unit tests RED**

```powershell
.\mvnw.cmd -Dtest=ExerciseServiceTest,ExerciseServiceReviewFlowTest test
```

Expected: failures show the old per-word generation/cache path is still active.

- [ ] **Step 4: Replace the old review path**

Wire the new collaborators into `ExerciseService`. Build one context per request, schedule batch assignments, reserve types, create quizzes in memory, and reschedule expected content failures. Remove old final-quiz cache methods and per-type marker methods after tests no longer reference them.

- [ ] **Step 5: Add bounded single-word candidate query**

Add a pageable repository query ordered by level and due time, request at most 32 candidates, and always append the target ID if absent.

- [ ] **Step 6: Add feature flags**

```properties
review.balanced-scheduler.enabled=true
review.snapshot-cache.enabled=true
```

Snapshot-disabled mode still uses the request-scoped bulk loader. Scheduler-disabled mode uses an eligible random type without reintroducing per-type Redis calls.

- [ ] **Step 7: Run service tests GREEN**

```powershell
.\mvnw.cmd -Dtest=ExerciseServiceTest,ExerciseServiceReviewFlowTest test
```

Expected: all service review tests pass.

---

### Task 8: Add After-Commit Invalidation, Metrics, Documentation, and Full Verification

**Files:**
- Create: `src/main/java/com/bachdauduc/vocab_app/service/review/ReviewVocabDataChangedEvent.java`
- Create: `src/main/java/com/bachdauduc/vocab_app/service/review/ReviewVocabCacheInvalidationListener.java`
- Create: `src/test/java/com/bachdauduc/vocab_app/service/review/ReviewVocabCacheInvalidationListenerTest.java`
- Modify: `src/main/java/com/bachdauduc/vocab_app/service/GeneratedWordExamplePersistenceService.java`
- Modify: `src/main/java/com/bachdauduc/vocab_app/service/InsertWordInfoService.java`
- Modify: `src/main/java/com/bachdauduc/vocab_app/service/GetWordDataService.java`
- Modify: `src/main/java/com/bachdauduc/vocab_app/service/WordExampleGenerationService.java`
- Modify: `src/main/resources/application.properties`
- Modify: `docs/frontend_context.md`
- Modify: `README.md`

**Interfaces:**
- Consumes: committed word-data mutations and Micrometer `MeterRegistry`.
- Produces: revision increments after commit, latency/distribution metrics, and an operator-facing Redis/file migration record.

- [ ] **Step 1: Write failing invalidation tests**

Assert rollback does not increment revision, commit increments once per distinct word, generated examples publish changed word IDs, and Redis invalidation failure does not roll back database writes.

- [ ] **Step 2: Run invalidation tests RED**

```powershell
.\mvnw.cmd -Dtest=ReviewVocabCacheInvalidationListenerTest,GeneratedWordExamplePersistenceServiceTest test
```

Expected: failures show no after-commit revision event exists.

- [ ] **Step 3: Implement event publication and listener**

Publish immutable word-ID sets from successful mutation services and handle them after commit. Increment `review_vocab_revision:v1:{wordId}` once per word; catch Redis failures after recording a counter and warning.

- [ ] **Step 4: Add metrics**

Use Micrometer timers/counters for total review, selection, preflight/Groq, cache read, DB load, progress, scheduling, quiz build, cache hit/miss/invalid, generated/skipped quiz, fallback, reservation conflict, and generated count by exercise type.

- [ ] **Step 5: Update documentation**

Document new keys, retired keys, TTLs, feature flags, synchronous Groq behavior, balanced distribution, invalidation behavior, and unchanged FE contract. Explicitly state that legacy `review_quiz:v2:*` and `current_review:*` are no longer written and expire naturally.

- [ ] **Step 6: Run focused and full verification**

```powershell
.\mvnw.cmd -Dtest=BalancedReviewQuizSchedulerTest,ReviewRequestContextTest,ReviewSnapshotCacheTest,ReviewVocabDataLoaderTest,ReviewProgressStoreTest,ReviewQuizFactoryTest,ExerciseServiceTest,ExerciseServiceReviewFlowTest,ReviewVocabCacheInvalidationListenerTest test
.\mvnw.cmd test
.\mvnw.cmd clean package
```

Expected: all tests pass and package completes successfully.

- [ ] **Step 7: Audit delivered changes**

Record:

- Every Redis key added, stopped, retained, and its TTL.
- Every file created, modified, or deleted.
- Every API behavior intentionally changed or preserved.
- Test commands and their exact results.
- Any dirty-worktree files that remained outside this feature.

