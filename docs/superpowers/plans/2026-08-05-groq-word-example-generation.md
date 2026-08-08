# GROQ Word Example Generation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ensure every standard `(wordId, senseId)` has at least four English/Vietnamese example pairs before vocabulary review quizzes are assembled.

**Architecture:** A batch preflight service calls a strict structured GROQ generator, then a transactional persistence service writes both example tables. `ExerciseService` runs preflight before its existing quiz loop.

**Tech Stack:** Java 21, Spring Boot 4, Java `HttpClient`, Jackson, Spring Data JPA, JUnit 5, Mockito, AssertJ.

## Global Constraints

- Process only standard `senseId` records; skip MOCHI/localized senses.
- Use `openai/gpt-oss-120b`, strict JSON Schema, and correlation by `requestId`, `wordId`, `senseId`.
- Persist `lang_code=vi`, `review_status=1`; count distinct nonblank examples and generate only the deficit needed to reach four.
- Preserve unrelated worktree changes and do not create commits.

---

### Task 1: Structured GROQ generator

**Files:** create generator input/output records, `WordExampleGenerator`, `HttpClientConfig`, and `GroqWordExampleGenerator`; test `GroqWordExampleGeneratorTest`; modify `ErrorCode` and `application.properties`.

**Produces:** `List<GeneratedWordExample> generate(List<WordExampleGenerationInput> inputs)`.

- [ ] Write failing tests that capture the HTTP request and assert model, system/user messages, strict schema, IDs, parsing, mismatches, duplicates, blank text, and missing target word.
- [ ] Run `.\mvnw.cmd -Dtest=GroqWordExampleGeneratorTest test`; expect RED.
- [ ] Implement input records containing `requestId`, `wordId`, `senseId`, `word`, `pos`, `level`, `englishSense`, and `requiredExampleCount`; parse each output item containing the three IDs plus an `examples` array of English/Vietnamese pairs.
- [ ] Build the exact lexicographer prompt and strict closed JSON Schema; require exactly the requested number of distinct sentences and validate output identity semantically.
- [ ] Add error code 2029 and `groq.api.key=${GROQ_API_KEY:${GROK_API_KEY:}}`; point legacy `grok.api.key` to it.
- [ ] Run the focused test again; expect GREEN.

---

### Task 2: Atomic persistence

**Files:** create `GeneratedWordExamplePersistenceService.java` and its test; modify `WordExampleRepository.java`.

**Produces:** `void persist(List<GeneratedWordExample> generatedExamples)`.

- [ ] Write failing tests for both table rows and exact fields: UUIDs, `AI_GENERATED`, `GROQ:openai/gpt-oss-120b`, `vi`, and review status `1`.
- [ ] Test that three existing distinct examples permit one insert, while four permit none.
- [ ] Run `.\mvnw.cmd -Dtest=GeneratedWordExamplePersistenceServiceTest test`; expect RED.
- [ ] Implement `@Transactional` persistence; normalize duplicates and recount before each insert, stopping at four.
- [ ] Run the focused test again; expect GREEN.

---

### Task 3: Batch preflight orchestration

**Files:** create `WordExampleGenerationService.java` and its test; add `findBySenseIdIn(...)` to `WordExampleRepository`.

**Produces:** `void ensureExamples(List<UserVocabulary> vocabularies)`.

- [ ] Test skipping MOCHI, pair deduplication, counts at four, deficits `4/3/2/1`, one GROQ call, and persistence handoff.
- [ ] Test that GROQ failure is logged but does not fail review preflight.
- [ ] Run `.\mvnw.cmd -Dtest=WordExampleGenerationServiceTest test`; expect RED.
- [ ] Batch-load words/senses/examples and build one input per deficient pair with a UUID request ID and `requiredExampleCount`.
- [ ] Call the generator once only when inputs are nonempty; pass valid results to persistence without logging definitions.
- [ ] Run the focused test again; expect GREEN.

---

### Task 4: ExerciseService integration

**Files:** modify `ExerciseService.java`; test `ExerciseServiceTest.java`; update `README.md` and `docs/frontend_context.md`.

- [ ] Write a failing test that verifies `getReviewVocabs` passes the selected list to `ensureExamples` exactly once before quiz assembly; cover the single-vocab endpoint too.
- [ ] Write a failing fallback test for `WORD_EXAMPLE_NOT_FOUND` on the four sentence-dependent types.
- [ ] Run `.\mvnw.cmd -Dtest=ExerciseServiceTest test`; expect RED.
- [ ] Inject `WordExampleGenerationService`; invoke preflight once for the batch and once with the target vocabulary for the single endpoint.
- [ ] Add `isExampleExerciseType` containing `VOCAB_CHOOSE_WORD_IN_SENTENCE_BLANK`, `VOCAB_FILL_WORD_IN_SENTENCE_BLANK`, `VOCAB_SENTENCE_TO_MEANING`, and `VOCAB_SENTENCE_BLANK_TO_SOUND`.
- [ ] When only one of those types throws `WORD_EXAMPLE_NOT_FOUND`, mark that type reviewed and continue selecting another type.
- [ ] Document minimum four examples, GROQ source, Vietnamese localization, and fallback behavior.
- [ ] Run focused generator, persistence, preflight, and exercise tests; expect GREEN.

---

### Task 5: Full verification

- [ ] Run `.\mvnw.cmd test`; require `BUILD SUCCESS`, zero failures, and zero errors.
- [ ] Run `git diff --check`; require no whitespace errors.
- [ ] Review the scoped diff: minimum four per `(wordId,senseId)`, exact ID mapping, correct fields in both tables, MOCHI untouched, and no unrelated user changes overwritten.
