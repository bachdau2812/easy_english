# Groq Writing Review Rate-Limit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Reduce Groq token consumption and handle writing-review rate limits with one bounded retry and a correct HTTP 429 response.

**Architecture:** Keep `GroqWritingReview` as the provider boundary, retain two task prompts and local response validation, and make the model prefix cacheable by moving exercise data to the user message. Add an in-process permit and a two-attempt response loop around Java HttpClient.

**Tech Stack:** Java 21, Spring Boot, Java HttpClient, Jackson, JUnit 5, Mockito, AssertJ.

## Global Constraints

- Preserve the existing review JSON response contract.
- Make no more than two Groq calls per review.
- Wait no more than 30 seconds for a provider-directed retry.
- Do not log learner or provider-generated private content.

---

### Task 1: Token-efficient request

**Files:** `GroqWritingReview.java`, both prompt files, and `GroqWritingReviewTest.java`.

- [x] Add failing request-body assertions for token settings and static/dynamic message separation.
- [x] Run `mvn -Dtest=GroqWritingReviewTest test` and confirm failure.
- [x] Update request construction and condense duplicated prompt wording while preserving the IELTS rubric.
- [x] Re-run the focused test and confirm it passes.

### Task 2: Bounded retry and rate-limit response

**Files:** `GroqWritingReview.java`, `ErrorCode.java`, and `GroqWritingReviewTest.java`.

- [x] Add failing tests for a successful zero-delay 429 retry, a second 429 mapping to the new error, and a maximum of two total calls.
- [x] Run the focused test and verify the expected failures.
- [x] Implement one shared two-attempt loop and parse `Retry-After`; wait only when it is at most 30 seconds and otherwise return HTTP 429 immediately.
- [x] Re-run the focused test and confirm it passes.

### Task 3: Local concurrency and safe metrics

**Files:** `GroqWritingReview.java` and `GroqWritingReviewTest.java`.

- [x] Add a failing concurrency test using latches and a permit-release regression test.
- [x] Implement a single fail-fast permit around provider execution and safe numeric usage/header logging.
- [x] Run the focused test until green.
- [x] Run `mvn test` and `git diff --check`, then review response-contract and privacy behavior.

## Completion notes (2026-09-07)

- Request: GPT OSS 120B, low reasoning, 5000 completion tokens; static system prompt and dynamic task data in JSON user message.
- Both prompts shortened to approximately 710/780 words, retaining task-specific scoring rules and the existing output keys.
- At most two provider calls per review, shared across JSON and quota retries. Missing/invalid Retry-After uses a one-second fallback; waits above 30 seconds are not attempted.
- A semaphore rejects concurrent requests in the same instance; finally releases it after success, provider errors, parse failures or interruptions.
- New error 2032 uses HTTP 429 through the existing AppException handler.
- Log metrics contain only validated token counts and rate-limit durations. No essay, review or provider error message is logged.
- Verification: `mvn -o test` passed: 177 tests, zero failures/errors, two opt-in live tests skipped. Includes 29 Groq unit cases; `git diff --check` passed.
- No dependency, database migration or new environment configuration is required. In-process concurrency control does not coordinate multiple deployments or other services sharing the Groq organization.


- Opt-in live verification also passed for both synthetic tasks on 2026-09-07: each returned HTTP 200 on attempt 1 with the original JSON contract validated. Task 1: prompt=1670, completion=1299, total=2969, reasoning=209. Task 2: prompt=1681, completion=1572, total=3253, reasoning=43. The provider did not report cached-token counts, so cache hits were not verified. These samples do not establish grading consistency across all essays.
