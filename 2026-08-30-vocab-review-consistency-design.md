# Vocab Review Consistency Design

## Goal

Make the ready-review count, generated review batch, and review-schedule updates
describe the same set of saved vocabulary records. A learner must not repeatedly
see due vocabulary left behind merely because quiz generation silently skipped
some records.

## Confirmed business rules

- A saved vocabulary record (`userVocabId`) is the unit of review, even when a
  user saves multiple senses belonging to the same `wordId`.
- `nextReviewAt <= now` is necessary but not sufficient for a vocabulary to be
  reported as ready: the vocabulary must also have enough valid data and an
  available exercise type to produce a quiz.
- The first submitted answer while a vocabulary is due determines that review
  turn. Once it moves `nextReviewAt` into the future, later retry answers are
  stored as attempts but cannot update level, correct turns, or schedule.
- Therefore, if the learner answers incorrectly and later retries correctly in
  the same turn, the turn remains incorrect.
- Existing level thresholds and review intervals remain unchanged:
  - levels 1, 2, and 3 advance after one correct turn;
  - level 4 advances after two correct turns;
  - level 5 advances after four correct turns;
  - level 6 remains the maximum level;
  - level 4 wrong answers schedule the next review after 12 hours;
  - level 5 wrong answers schedule the next review after 1 day.
- Batch sizes remain 30, 60, and 90. A response may be smaller only when fewer
  ready and generatable vocabularies exist.
- API request and response shapes remain unchanged.

## Root causes in the current implementation

1. `VOCAB_REVIEW` counts all due database rows, but quiz generation can omit a
   row because its snapshot is missing, it has no eligible exercise type, or all
   types are reserved. This makes the displayed count larger than the number of
   quizzes a learner can actually complete.
2. Exercise progress is keyed by `userId + wordId`, while attempts and schedules
   target `userVocabId`. Different saved senses of one word therefore consume
   each other's available exercise types.
3. The batch selects at most the requested number before eligibility and Redis
   reservation are known. Skipped selected records are not replaced by later
   due candidates.
4. Correct answers always update the schedule. A correct retry after a wrong
   answer can promote the vocabulary and overwrite the wrong-answer schedule.
5. Schedule updates do not lock the vocabulary row, so duplicate or concurrent
   submissions can both observe the same due state and update it twice.
6. Vocab attempt validation checks only that `userVocabId` exists, not that it
   belongs to the submitted `userId`.
7. The declared quota for a 90-word batch sums to 95. The overall cap silently
   truncates the final level allocation.

## Architecture

### Review candidate ordering

The repository continues to load due vocabulary in deterministic priority order
(`level`, then `nextReviewAt`). `ExerciseService` builds a quota-prioritized
candidate order for the requested size but retains overflow candidates. Quiz
generation walks that order until it emits the requested number or exhausts all
candidates. A skipped candidate therefore does not waste a batch slot when a
later valid candidate exists.

The 30- and 60-word quota maps remain unchanged. The 90-word quota is corrected
to sum to 90 by using the effective allocation already produced by the current
overall cap: levels 1 through 6 receive `25, 25, 14, 13, 10, 3`. Shortages at a
level continue to be redistributed to other levels.

### Per-saved-vocabulary progress

`ReviewProgressStore` uses `userVocabId`, not `wordId`, in the progress key. The
key remains user-scoped and keeps the existing two-hour reservation lifetime.
This prevents two saved senses of the same word from blocking one another.

The Redis pattern moves to a new version so existing word-scoped values cannot
collide with the corrected identity. Old keys are not deleted and expire through
their existing TTL.

### Shared availability evaluation

A focused review-availability component evaluates due candidates using the same
snapshot loader, quiz eligibility rules, and progress state as batch generation.
It exposes a read-only count operation for `VOCAB_REVIEW`; this operation does
not reserve an exercise type and does not generate examples.

If Redis is unavailable, availability follows the existing request-local
fallback and treats eligible types as available. Invalid word/sense references,
blank required content, and candidates with no eligible type are excluded from
the ready count and logged by ID and reason without logging private content.

### Schedule update and concurrency

Submitting a vocab attempt loads the targeted `UserVocabulary` with a database
write lock and verifies `userVocabulary.userId == request.userId`. The attempt is
always persisted after validation.

The schedule is updated only when `nextReviewAt` is null or not later than the
current application time. The first transaction moves it into the future;
duplicate submissions and retry answers then observe a non-due record and skip
all level/turn/time changes. The existing Redis wrong-answer marker is no longer
the source of truth for schedule idempotency and can be retired after its old
keys expire.

All calculations in one update use one captured timestamp. This prevents small
differences between branches and makes interval tests deterministic.

## Data flow

1. The ready-count endpoint loads due candidates and evaluates them without
   reserving quiz types.
2. The batch endpoint orders all due candidates according to quota priority.
3. It preloads review data, computes eligible exercise types, and walks the
   ordered candidates.
4. For each candidate it reserves one type under the candidate's
   `userVocabId`; failures are logged and the next candidate is tried.
5. The endpoint stops after emitting the requested quota or exhausting all
   ready candidates.
6. On submit, the service locks and validates the vocabulary, stores the
   attempt, and updates the schedule only if that vocabulary is still due.

## Error handling and observability

- A vocab owned by another user returns `USER_VOCABULARY_NOT_FOUND` and neither
  an attempt nor a schedule update is written.
- A missing or invalid snapshot does not fail the whole batch. It is excluded
  from ready count and batch output with a structured skip reason.
- Redis failures keep the existing database/in-memory fallback behavior.
- Batch logs include due count, eligible count, requested count, emitted count,
  skipped count by reason, and emitted count by level.
- Schedule logs distinguish `updated` from `skipped_not_due`.

## Testing

Tests must cover:

- quota totals and ordering for 30, 60, and 90;
- backfilling when an early candidate cannot create a quiz;
- two `userVocabId` values sharing one `wordId` without progress collision;
- ready count excluding invalid, ineligible, and currently exhausted targets;
- correct level 4 and level 5 intervals and thresholds;
- wrong level 4 and level 5 intervals;
- wrong followed by correct retry preserving the wrong schedule;
- duplicate/concurrent submissions updating a due schedule once;
- ownership rejection before attempt persistence;
- Redis fallback behavior;
- focused review tests, the full Maven test suite, and a clean package build.

## Compatibility and migration

- No database migration is required.
- No controller or DTO contract changes are required.
- A new versioned Redis progress pattern is deployed; legacy keys expire
  naturally.
- Existing unrelated working-tree changes remain untouched.
