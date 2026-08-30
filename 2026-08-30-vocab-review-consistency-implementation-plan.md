# Vocab Review Consistency Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make ready-review counts, generated batches, and first-attempt schedule updates consistent for each saved vocabulary record.

**Architecture:** Introduce a shared read-only availability service and a focused quota selector. Track Redis exercise progress by `userVocabId`, generate from an overflow-capable ordered candidate list, and make schedule updates due-state guarded under a database write lock.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring Data JPA, Spring Data Redis, JUnit 5, Mockito, AssertJ, Maven.

## Global Constraints

- Keep controller and DTO contracts unchanged.
- Treat `userVocabId` as the unit of review.
- The first submitted answer while a vocabulary is due determines the turn.
- Retry attempts are persisted but cannot update a future schedule.
- Preserve all existing level thresholds and intervals.
- Preserve unrelated working-tree changes.
- Follow RED-GREEN-REFACTOR for every production change.

---

### Task 1: Correct progress identity and expose read-only availability

**Files:**
- Modify: `src/main/resources/redis_keys.properties`
- Modify: `src/main/java/com/bachdauduc/vocab_app/service/review/ReviewProgressStore.java`
- Modify: `src/test/java/com/bachdauduc/vocab_app/service/review/ReviewProgressStoreTest.java`

**Interfaces:**
- Consumes: `userId`, `userVocabId`, and eligible `ExerciseType` values.
- Produces: `reserveFirstAvailable(String, String, List<ExerciseType>)`, `availableTypes(String, String, Set<ExerciseType>)`, and `release(String, String, ExerciseType)` where the second identifier is always `userVocabId`.

- [ ] **Step 1: Write failing tests**

Add tests proving the Redis key is built from `userVocabId`, active sorted-set members are removed from the returned eligible set, expired members do not block availability, and Redis failure returns every eligible type.

- [ ] **Step 2: Verify RED**

Run:

```powershell
mvn "-Dtest=ReviewProgressStoreTest" test
```

Expected: compilation/test failure because `availableTypes` and the new identity behavior do not exist.

- [ ] **Step 3: Implement minimal progress changes**

Change the key to a new pattern:

```properties
redis.key.review-progress=review_progress:v3:%s:%s
```

Add a read-only Redis sorted-set lookup using scores later than the captured current time. Catch Redis runtime failures and return the input eligible set. Rename `wordId` parameters and logs to `userVocabId`.

- [ ] **Step 4: Verify GREEN**

Run the focused test and confirm all progress-store tests pass.

---

### Task 2: Share availability between ready count and batch generation

**Files:**
- Create: `src/main/java/com/bachdauduc/vocab_app/service/review/ReviewAvailabilityService.java`
- Create: `src/test/java/com/bachdauduc/vocab_app/service/review/ReviewAvailabilityServiceTest.java`
- Modify: `src/main/java/com/bachdauduc/vocab_app/service/UserVocabularyService.java`
- Modify: `src/test/java/com/bachdauduc/vocab_app/service/UserVocabularyInfoServiceTest.java`

**Interfaces:**
- Consumes: user ID, due `UserVocabulary` list, and language code.
- Produces: `List<UserVocabulary> findAvailable(String userId, List<UserVocabulary> due, String langCode)` preserving input order.

- [ ] **Step 1: Write failing availability tests**

Cover a valid snapshot with an available base type, missing snapshot, no eligible type, all types reserved, two saved senses sharing one word, and Redis fallback.

- [ ] **Step 2: Verify RED**

Run:

```powershell
mvn "-Dtest=ReviewAvailabilityServiceTest,UserVocabularyInfoServiceTest" test
```

Expected: compilation failure because the availability service does not exist.

- [ ] **Step 3: Implement shared evaluation**

Load snapshots once, create one `ReviewRequestContext`, calculate eligible types through `ReviewQuizFactory`, and retain candidates for which `ReviewProgressStore.availableTypes` is non-empty.

Change `VOCAB_REVIEW` info to load due vocabularies, evaluate with language `vi`, and return the available list size instead of the raw due-row count.

- [ ] **Step 4: Verify GREEN**

Run both focused test classes and confirm the ready count matches availability.

---

### Task 3: Correct quota ordering and backfill skipped batch slots

**Files:**
- Create: `src/main/java/com/bachdauduc/vocab_app/service/review/ReviewVocabSelector.java`
- Create: `src/test/java/com/bachdauduc/vocab_app/service/review/ReviewVocabSelectorTest.java`
- Modify: `src/main/java/com/bachdauduc/vocab_app/service/ExerciseService.java`
- Modify: `src/test/java/com/bachdauduc/vocab_app/service/ExerciseServiceTest.java`

**Interfaces:**
- Produces: `List<UserVocabulary> orderCandidates(List<UserVocabulary> available, int requested)` with quota-prioritized candidates first and all overflow candidates retained.
- `ExerciseService.generateReviewQuizzes` accepts a maximum emitted count and stops only after reaching it or exhausting candidates.

- [ ] **Step 1: Write failing selector and flow tests**

Assert quota totals for 30/60/90, the corrected 90 allocation `25,25,14,13,10,3`, redistribution under shortages, preservation of overflow, two `userVocabId` values sharing one `wordId`, and replacement of an early reservation failure by a later candidate.

- [ ] **Step 2: Verify RED**

Run:

```powershell
mvn "-Dtest=ReviewVocabSelectorTest,ExerciseServiceTest" test
```

Expected: compilation/test failures because the selector and max-emission/backfill flow do not exist.

- [ ] **Step 3: Implement minimal selector and integration**

Filter due vocabularies through `ReviewAvailabilityService`, order all available candidates, preflight the first requested candidates, then schedule/reserve/build candidates until the response reaches the requested size. Pass `userVocabId` to progress reservation and release.

- [ ] **Step 4: Verify GREEN**

Run selector, exercise-service, scheduler, loader, factory, and progress-store tests.

---

### Task 4: Make first due attempt authoritative and ownership-safe

**Files:**
- Modify: `src/main/java/com/bachdauduc/vocab_app/repository/UserVocabularyRepository.java`
- Modify: `src/main/java/com/bachdauduc/vocab_app/configuration/TimeZoneConfig.java`
- Modify: `src/main/java/com/bachdauduc/vocab_app/service/UserVocabularyService.java`
- Modify: `src/main/java/com/bachdauduc/vocab_app/properties/RedisKeyProperties.java`
- Modify: `src/main/resources/redis_keys.properties`
- Modify: `src/test/java/com/bachdauduc/vocab_app/service/UserVocabularyReviewScheduleServiceTest.java`

**Interfaces:**
- Produces: `Optional<UserVocabulary> findByIdForUpdate(String id)` under `PESSIMISTIC_WRITE`.
- Provides one application `Clock` bean based on the configured application timezone.

- [ ] **Step 1: Write failing schedule tests**

Cover exact level 4/5 correct and wrong intervals using a fixed clock, wrong followed by correct retry, duplicate correct retry, ownership rejection before attempt save, and use of the locking repository method.

- [ ] **Step 2: Verify RED**

Run:

```powershell
mvn "-Dtest=UserVocabularyReviewScheduleServiceTest" test
```

Expected: failures show correct retries overwrite schedules, ownership is unchecked, and time cannot be asserted deterministically.

- [ ] **Step 3: Implement due-state guard and lock**

Load vocab targets with the write-lock query before saving the attempt, verify ownership, capture `LocalDateTime.now(clock)` once, and update schedule only when `nextReviewAt == null || !nextReviewAt.isAfter(now)`. Pass the captured time into correct/wrong calculations.

Remove the obsolete `current_review_wrong` scheduling dependency and configuration; old Redis keys expire naturally.

- [ ] **Step 4: Verify GREEN**

Run the focused schedule tests and confirm every interval and retry case passes.

---

### Task 5: Documentation and full verification

**Files:**
- Modify: `docs/frontend_context.md`
- Modify: `docs/vocab-review-quiz-implementation.md`

- [ ] **Step 1: Update behavior documentation**

Document per-`userVocabId` progress key v3, availability-based ready count, quota backfill, first-due-attempt scheduling, retry behavior, and retirement of `current_review_wrong`.

- [ ] **Step 2: Run focused regression tests**

```powershell
mvn "-Dtest=ReviewProgressStoreTest,ReviewAvailabilityServiceTest,ReviewVocabSelectorTest,ExerciseServiceTest,UserVocabularyInfoServiceTest,UserVocabularyReviewScheduleServiceTest,BalancedReviewQuizSchedulerTest,ReviewVocabDataLoaderTest,ReviewQuizFactoryTest" test
```

Expected: zero failures and zero errors.

- [ ] **Step 3: Run full verification**

```powershell
mvn test
mvn clean package
git diff --check
git status --short
```

Expected: both Maven commands exit successfully, the diff has no whitespace errors, and unrelated untracked files remain untouched.
