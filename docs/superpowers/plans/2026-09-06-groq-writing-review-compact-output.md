# Groq Writing Review Compact Output Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reduce provider prompt complexity and recover from intermittent Groq JSON-generation failures while preserving the stored and returned writing-review JSON contract.

**Architecture:** Keep the existing JSON Schemas as the application's validation source of truth. Derive a compact JSON template from the selected schema for the provider prompt, then retry the identical request once only when Groq returns `json_validate_failed`.

**Tech Stack:** Java 21, Spring Boot, Jackson, Java `HttpClient`, JUnit 5, Mockito, AssertJ, Maven.

## Global Constraints

- Keep `src/main/resources/prompts/ielts-writing-task-1.txt` and `ielts-writing-task-2.txt` unchanged.
- Preserve the stored review JSON, endpoint response, and database schema.
- Never log learner essays or generated review content.
- Use JSON object mode, temperature `0.2`, medium reasoning effort, and the existing completion-token limit.
- Retry at most once, and only for provider code `json_validate_failed`.

---

### Task 1: Compact Provider Output Template

**Files:**
- Modify: `src/main/java/com/bachdauduc/vocab_app/service/implementation/GroqWritingReview.java`
- Test: `src/test/java/com/bachdauduc/vocab_app/service/implementation/GroqWritingReviewTest.java`

**Interfaces:**
- Consumes: loaded `JsonNode` response schemas.
- Produces: `private JsonNode buildOutputTemplate(JsonNode schema)`, used when constructing the system prompt.

- [ ] **Step 1: Write the failing request-body test**

Update the existing request-body test to capture the system message and assert that it contains representative array values while omitting schema metadata:

```java
String systemPrompt = body.at("/messages/0/content").asText();
assertThat(systemPrompt)
        .contains("\"grammarErrors\":[{", "\"strengthsVi\":[\"\"]")
        .doesNotContain("additionalProperties", "\"required\"");
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
mvn '-Dtest=GroqWritingReviewTest#generateReviewUsesLowTemperatureJsonAndTreatsEssayAsJsonData' test
```

Expected: FAIL because the current system message includes the full schema and therefore contains `additionalProperties` and `required`.

- [ ] **Step 3: Implement recursive template creation**

Add a schema-to-template method with one representative array element:

```java
private JsonNode buildOutputTemplate(JsonNode schema) {
    return switch (schema.path("type").asText()) {
        case "object" -> {
            ObjectNode result = objectMapper.createObjectNode();
            schema.path("properties").fields()
                    .forEachRemaining(field -> result.set(field.getKey(), buildOutputTemplate(field.getValue())));
            yield result;
        }
        case "array" -> objectMapper.createArrayNode().add(buildOutputTemplate(schema.path("items")));
        case "integer", "number" -> objectMapper.getNodeFactory().numberNode(0);
        case "boolean" -> objectMapper.getNodeFactory().booleanNode(false);
        case "string" -> objectMapper.getNodeFactory().textNode("");
        default -> throw new IllegalStateException("Unsupported writing review schema type");
    };
}
```

Replace the full-schema suffix with compact-template instructions and `buildOutputTemplate(schema).toString()`.

- [ ] **Step 4: Run the focused test and verify GREEN**

Run the command from Step 2. Expected: one test passes with zero failures.

### Task 2: One Retry for Provider JSON Validation Failure

**Files:**
- Modify: `src/main/java/com/bachdauduc/vocab_app/service/implementation/GroqWritingReview.java`
- Test: `src/test/java/com/bachdauduc/vocab_app/service/implementation/GroqWritingReviewTest.java`

**Interfaces:**
- Consumes: Groq HTTP response body.
- Produces: `private boolean isJsonValidationFailure(HttpResponse<String> response)` and a maximum of two `HttpClient.send` calls.

- [ ] **Step 1: Write the failing retry test**

Replace `doesNotRetryJsonValidationFailure` with a test that returns a 400 `json_validate_failed` response followed by a valid response and verifies the service returns the review after exactly two sends:

```java
when(httpClient.send(any(HttpRequest.class), any()))
        .thenReturn(failedResponse, successfulResponse);

assertThat(reviewService.generateReview("exercise-1", "user-1", "My essay"))
        .contains("\"overallBand\":6.5");
verify(httpClient, times(2)).send(any(HttpRequest.class), any());
```

Keep `doesNotRetryOtherBadRequests` and verify it still performs exactly one send.

- [ ] **Step 2: Run retry tests and verify RED**

Run:

```powershell
mvn '-Dtest=GroqWritingReviewTest#retriesJsonValidationFailureOnce+doesNotRetryOtherBadRequests' test
```

Expected: the retry test fails with `WRITING_REVIEW_FAILED` because the service currently sends once.

- [ ] **Step 3: Implement the bounded retry**

After the first send, retry once only when status is 400 and the parsed provider error code equals `json_validate_failed`:

```java
HttpResponse<String> response = send(request);
if (isJsonValidationFailure(response)) {
    log.warn("Retrying Groq writing review after JSON validation failure: exerciseId={}", exerciseId);
    response = send(request);
}
```

The helper parses only `error.code`; parse failures return `false`. Existing sanitized final-error logging remains in place.

- [ ] **Step 4: Run retry tests and verify GREEN**

Run the command from Step 2. Expected: both tests pass with zero failures.

### Task 3: Regression and Live Provider Verification

**Files:**
- Verify: `src/main/java/com/bachdauduc/vocab_app/service/implementation/GroqWritingReview.java`
- Verify: `src/test/java/com/bachdauduc/vocab_app/service/implementation/GroqWritingReviewTest.java`
- Verify: `src/test/java/com/bachdauduc/vocab_app/service/implementation/GroqWritingReviewLiveTest.java`

**Interfaces:**
- Consumes: the completed compact-template and retry behavior.
- Produces: verified provider-compatible behavior for synthetic Task 1 and Task 2 submissions.

- [ ] **Step 1: Run all deterministic tests**

```powershell
mvn test
```

Expected: build success with zero failures and errors; live tests remain skipped unless explicitly enabled.

- [ ] **Step 2: Run opt-in Task 1 and Task 2 live tests**

```powershell
mvn '-Dtest=GroqWritingReviewLiveTest' '-Dgroq.live=true' test
```

Expected: two tests pass, provider output validates against the full local schema, and logs contain no learner essay or generated content.

- [ ] **Step 3: Review the final diff**

```powershell
git diff --check
git diff -- src/main/java/com/bachdauduc/vocab_app/service/implementation/GroqWritingReview.java src/test/java/com/bachdauduc/vocab_app/service/implementation/GroqWritingReviewTest.java
```

Expected: no whitespace errors; changes are limited to the compact provider template, bounded retry, and their tests.
