# User Vocabulary Info API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add one My Vocab API that returns either total/per-level vocabulary counts or the number currently due for review.

**Architecture:** Extend the existing user-vocabulary controller and service. Execute one repository aggregate based on `infoType`, map the result into a shared response DTO, and keep unrelated response fields null. Use database counts without Redis caching.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring MVC, Spring Data JPA, Lombok, JUnit 5, Mockito, AssertJ, MockMvc.

## Global Constraints

- Endpoint is `GET /user-vocabularies/info?userId={userId}&infoType={infoType}`.
- Supported types are exactly `VOCAB_QUANTITY` and `VOCAB_REVIEW`.
- `VOCAB_QUANTITY` always returns levels 1 through 6 in ascending order, including zero counts.
- `VOCAB_REVIEW` counts only `nextReviewAt <= LocalDateTime.now()`; null dates do not count.
- Invalid `infoType` returns an `AppException` with a new HTTP 400 error code.
- No Redis cache, database migration, or change to existing endpoint contracts.

---

### Task 1: Response types and repository aggregates

**Files:**
- Create: `src/main/java/com/bachdauduc/vocab_app/constant/UserVocabularyInfoType.java`
- Create: `src/main/java/com/bachdauduc/vocab_app/dto/response/uservocabulary/UserVocabularyLevelQuantityResponse.java`
- Create: `src/main/java/com/bachdauduc/vocab_app/dto/response/uservocabulary/UserVocabularyInfoResponse.java`
- Create: `src/main/java/com/bachdauduc/vocab_app/repository/projection/UserVocabularyLevelQuantityProjection.java`
- Modify: `src/main/java/com/bachdauduc/vocab_app/repository/UserVocabularyRepository.java`
- Test: `src/test/java/com/bachdauduc/vocab_app/repository/UserVocabularyInfoRepositoryTest.java`

**Interfaces:**
- Produces: `List<UserVocabularyLevelQuantityProjection> countUserVocabularyByLevel(String userId)`
- Produces: `long countDueReviewVocabs(String userId, LocalDateTime now)`
- Produces: shared DTOs consumed by the service and controller tasks.

- [ ] **Step 1: Write failing repository tests**

Create a `@DataJpaTest` that inserts user vocab rows for two users and verifies:

```java
assertThat(repository.countUserVocabularyByLevel("user-1"))
        .extracting(
                UserVocabularyLevelQuantityProjection::getLevel,
                UserVocabularyLevelQuantityProjection::getQuantity
        )
        .containsExactlyInAnyOrder(
                tuple(1, 2L),
                tuple(3, 1L)
        );

assertThat(repository.countDueReviewVocabs("user-1", now)).isEqualTo(2L);
```

The fixture must include one future `nextReviewAt`, one null `nextReviewAt`,
and one due row belonging to a different user.

- [ ] **Step 2: Run repository test to verify RED**

Run:

```powershell
.\mvnw.cmd -Dtest=UserVocabularyInfoRepositoryTest test
```

Expected: test compilation fails because the two repository methods and
projection do not exist.

- [ ] **Step 3: Add the enum, DTOs, projection, and repository methods**

```java
public enum UserVocabularyInfoType {
    VOCAB_QUANTITY,
    VOCAB_REVIEW
}
```

```java
public interface UserVocabularyLevelQuantityProjection {
    Integer getLevel();
    Long getQuantity();
}
```

Add repository queries:

```java
@Query("""
        SELECT uv.level AS level, COUNT(uv) AS quantity
        FROM UserVocabulary uv
        WHERE uv.userId = :userId
        GROUP BY uv.level
        ORDER BY uv.level
        """)
List<UserVocabularyLevelQuantityProjection> countUserVocabularyByLevel(
        @Param("userId") String userId
);

@Query("""
        SELECT COUNT(uv)
        FROM UserVocabulary uv
        WHERE uv.userId = :userId
          AND uv.nextReviewAt IS NOT NULL
          AND uv.nextReviewAt <= :now
        """)
long countDueReviewVocabs(
        @Param("userId") String userId,
        @Param("now") LocalDateTime now
);
```

`UserVocabularyInfoResponse` uses Lombok and nullable wrapper fields:

```java
String userId;
UserVocabularyInfoType infoType;
Long totalQuantity;
List<UserVocabularyLevelQuantityResponse> quantityByLevels;
Long reviewQuantity;
```

- [ ] **Step 4: Run repository test to verify GREEN**

Run:

```powershell
.\mvnw.cmd -Dtest=UserVocabularyInfoRepositoryTest test
```

Expected: all repository tests pass.

---

### Task 2: Service dispatch, validation, and level normalization

**Files:**
- Modify: `src/main/java/com/bachdauduc/vocab_app/exception/ErrorCode.java`
- Modify: `src/main/java/com/bachdauduc/vocab_app/service/UserVocabularyService.java`
- Create: `src/test/java/com/bachdauduc/vocab_app/service/UserVocabularyInfoServiceTest.java`

**Interfaces:**
- Consumes: repository methods and DTOs from Task 1.
- Produces: `UserVocabularyInfoResponse getUserVocabularyInfo(String userId, String infoType)`.

- [ ] **Step 1: Write failing service tests**

Build `UserVocabularyService` with Mockito mocks for its constructor
dependencies.

For `VOCAB_QUANTITY`, stub aggregate rows for levels 1, 3, and an invalid
level 8. Assert:

```java
assertThat(response.getInfoType()).isEqualTo(VOCAB_QUANTITY);
assertThat(response.getTotalQuantity()).isEqualTo(10L);
assertThat(response.getQuantityByLevels())
        .extracting(
                UserVocabularyLevelQuantityResponse::getLevel,
                UserVocabularyLevelQuantityResponse::getQuantity
        )
        .containsExactly(
                tuple(1, 2L),
                tuple(2, 0L),
                tuple(3, 3L),
                tuple(4, 0L),
                tuple(5, 0L),
                tuple(6, 0L)
        );
assertThat(response.getReviewQuantity()).isNull();
verify(repository, never()).countDueReviewVocabs(anyString(), any());
```

The invalid level 8 row has quantity 5 and is included only in
`totalQuantity`.

For `VOCAB_REVIEW`, assert `reviewQuantity` is returned, quantity fields are
null, and the level aggregate is never called.

Add tests asserting `USER_NOT_FOUND` and
`INVALID_USER_VOCABULARY_INFO_TYPE` for invalid input.

- [ ] **Step 2: Run service test to verify RED**

Run:

```powershell
.\mvnw.cmd -Dtest=UserVocabularyInfoServiceTest test
```

Expected: compilation fails because the service method and error code do not
exist.

- [ ] **Step 3: Add error code and minimal service implementation**

Add:

```java
INVALID_USER_VOCABULARY_INFO_TYPE(
        2030,
        "Invalid user vocabulary info type",
        HttpStatus.BAD_REQUEST
)
```

Parse with:

```java
private UserVocabularyInfoType parseInfoType(String infoType) {
    try {
        return UserVocabularyInfoType.valueOf(infoType.trim().toUpperCase(Locale.ROOT));
    } catch (RuntimeException exception) {
        throw new AppException(ErrorCode.INVALID_USER_VOCABULARY_INFO_TYPE);
    }
}
```

For quantity, collect all aggregate rows into a map, sum every non-null
quantity for `totalQuantity`, then use `IntStream.rangeClosed(1, 6)` to build
the ordered list.

For review, pass one captured `LocalDateTime.now()` value to
`countDueReviewVocabs`.

- [ ] **Step 4: Run service test to verify GREEN**

Run:

```powershell
.\mvnw.cmd -Dtest=UserVocabularyInfoServiceTest test
```

Expected: all service tests pass with no unnecessary Mockito stubs.

---

### Task 3: Controller contract and frontend documentation

**Files:**
- Modify: `src/main/java/com/bachdauduc/vocab_app/controller/UserVocabularyController.java`
- Create: `src/test/java/com/bachdauduc/vocab_app/controller/UserVocabularyInfoControllerTest.java`
- Modify: `docs/frontend_context.md`

**Interfaces:**
- Consumes: `UserVocabularyService.getUserVocabularyInfo(userId, infoType)`.
- Produces: `GET /user-vocabularies/info` returning
  `ApiResponse<UserVocabularyInfoResponse>`.

- [ ] **Step 1: Write failing MockMvc contract test**

Use `MockMvcBuilders.standaloneSetup(controller).build()`, mock the service
response, and assert:

```java
mockMvc.perform(get("/user-vocabularies/info")
                .param("userId", "user-1")
                .param("infoType", "VOCAB_QUANTITY"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(2000))
        .andExpect(jsonPath("$.result.infoType").value("VOCAB_QUANTITY"))
        .andExpect(jsonPath("$.result.totalQuantity").value(3))
        .andExpect(jsonPath("$.result.quantityByLevels.length()").value(6))
        .andExpect(jsonPath("$.result.quantityByLevels[0].level").value(1))
        .andExpect(jsonPath("$.result.quantityByLevels[5].level").value(6))
        .andExpect(jsonPath("$.result.reviewQuantity").isEmpty());
```

- [ ] **Step 2: Run controller test to verify RED**

Run:

```powershell
.\mvnw.cmd -Dtest=UserVocabularyInfoControllerTest test
```

Expected: HTTP 404 because `/user-vocabularies/info` is not mapped.

- [ ] **Step 3: Add controller mapping**

```java
@GetMapping("/info")
public ApiResponse<UserVocabularyInfoResponse> getUserVocabularyInfo(
        @RequestParam String userId,
        @RequestParam String infoType
) {
    log.info(
            "Request received: action=getUserVocabularyInfo, userId={}, infoType={}",
            userId,
            infoType
    );
    return success(
            "Get user vocabulary info successfully",
            userVocabularyService.getUserVocabularyInfo(userId, infoType)
    );
}
```

- [ ] **Step 4: Document endpoint and TypeScript response**

Add the endpoint to the Saved Vocabulary table in `docs/frontend_context.md`
and define:

```typescript
export type UserVocabularyInfoType = "VOCAB_QUANTITY" | "VOCAB_REVIEW";

export interface UserVocabularyLevelQuantityResponse {
  level: number;
  quantity: number;
}

export interface UserVocabularyInfoResponse {
  userId: string;
  infoType: UserVocabularyInfoType;
  totalQuantity?: number | null;
  quantityByLevels?: UserVocabularyLevelQuantityResponse[] | null;
  reviewQuantity?: number | null;
}
```

- [ ] **Step 5: Run focused tests**

Run:

```powershell
.\mvnw.cmd '-Dtest=UserVocabularyInfoRepositoryTest,UserVocabularyInfoServiceTest,UserVocabularyInfoControllerTest' test
```

Expected: all focused tests pass.

---

### Task 4: Full verification

**Files:**
- Verify all files changed in Tasks 1–3.

**Interfaces:**
- Produces: build evidence and final change inventory.

- [ ] **Step 1: Check formatting**

Run:

```powershell
git diff --check
```

Expected: no diff errors.

- [ ] **Step 2: Run clean package**

Run:

```powershell
.\mvnw.cmd clean package
```

Expected: `BUILD SUCCESS`, zero test failures/errors, and a repackaged Spring
Boot jar under `target/`.

- [ ] **Step 3: Review final diff**

Confirm:

- Only the requested API, DTOs, enum, projection, repository queries, error
  code, tests, and frontend documentation were changed.
- No Redis keys, schema migrations, dependencies, or existing response
  contracts changed.
