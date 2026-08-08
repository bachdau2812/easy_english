# Case-Sensitive Word DISTINCT Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Giữ tìm kiếm qua `normalized_word` nhưng trả kết quả unique theo `(word,pos,source,level)` với `word` phân biệt hoa/thường.

**Architecture:** Tái hiện collation không phân biệt hoa/thường bằng `VARCHAR_IGNORECASE` trong H2 repository test. Sửa ba native query trong `WordRepository` để chọn đại diện theo `BINARY word`; lớp dedup Java và API response giữ nguyên.

**Tech Stack:** Java 21, Spring Boot 4, Spring Data JPA, MySQL native SQL, H2 `@DataJpaTest`, JUnit 5, AssertJ.

## Global Constraints

- Điều kiện tìm kiếm exact/prefix vẫn dùng `normalized_word`.
- Chỉ `word` cần phân biệt hoa/thường trong khóa unique; `pos`, `word_source`, `cert_level` giữ hành vi hiện tại.
- Không thay đổi DTO, controller contract hoặc các query category/level.
- Không commit hoặc push; giữ toàn bộ thay đổi trong worktree theo lựa chọn trước đó của người dùng.

---

### Task 1: Repository regression and minimal query fix

**Files:**
- Create: `src/test/java/com/bachdauduc/vocab_app/repository/WordRepositoryTest.java`
- Modify: `src/main/java/com/bachdauduc/vocab_app/repository/WordRepository.java:14-48`

**Interfaces:**
- Consumes: `WordRepository.findByNormalizedWord(String)`, `findByNormalizedWordPrefix(String)`, `findUniqueWordsByNormalizedWordPrefix(String)`.
- Produces: Cùng signatures hiện tại, nhưng `Apple` và `apple` được giữ thành hai nhóm.

- [ ] **Step 1: Viết repository test tái hiện collation case-insensitive**

Tạo `WordRepositoryTest` dùng `@DataJpaTest`, `JdbcTemplate`, đổi riêng cột H2 thành `VARCHAR_IGNORECASE`, chèn hai dòng `Apple` trùng tuple và hai dòng `apple` trùng tuple:

```java
@DataJpaTest
class WordRepositoryTest {
    @Autowired WordRepository repository;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpCaseInsensitiveWordColumn() {
        jdbcTemplate.execute("ALTER TABLE words ALTER COLUMN word VARCHAR_IGNORECASE NOT NULL");
        jdbcTemplate.update("DELETE FROM words");
        insertWord("1", "Apple");
        insertWord("2", "Apple");
        insertWord("3", "apple");
        insertWord("4", "apple");
    }

    @Test
    void exactSearchGroupsByCaseSensitiveWordTuple() {
        assertThat(repository.findByNormalizedWord("apple"))
                .extracting(Word::getWord)
                .containsExactlyInAnyOrder("Apple", "apple");
    }

    @Test
    void prefixSearchGroupsByCaseSensitiveWordTuple() {
        assertThat(repository.findByNormalizedWordPrefix("app"))
                .extracting(Word::getWord)
                .containsExactlyInAnyOrder("Apple", "apple");
    }

    @Test
    void uniqueWordPrefixKeepsDifferentLetterCase() {
        assertThat(repository.findUniqueWordsByNormalizedWordPrefix("app"))
                .containsExactlyInAnyOrder("Apple", "apple");
    }
}
```

`insertWord` dùng `JdbcTemplate.update` và điền đủ `id`, `word`, `normalized_word='apple'`, `pos='noun'`, `lang='English'`, `lang_code='en'`, `word_source='LOCAL'`, `cert_level='B1'`, `created_at`, `updated_at`.

- [ ] **Step 2: Chạy test để xác nhận RED**

Run:

```powershell
.\mvnw.cmd -Dtest=WordRepositoryTest test
```

Expected: ba assertion chỉ nhận một cách viết do `GROUP BY`/`DISTINCT` dùng collation case-insensitive.

- [ ] **Step 3: Sửa tối thiểu ba native query**

Exact và prefix query đổi khóa nhóm:

```sql
GROUP BY BINARY w2.word, w2.pos, w2.word_source, w2.cert_level
```

Query `findUniqueWordsByNormalizedWordPrefix` đổi từ `SELECT DISTINCT` sang representative subquery:

```sql
SELECT w.word
FROM words w
JOIN (
    SELECT MIN(w2.id) AS id
    FROM words w2
    WHERE w2.normalized_word LIKE CONCAT(:normalizedPrefix, '%')
    GROUP BY BINARY w2.word
) representative ON representative.id = w.id
ORDER BY w.word ASC, w.id ASC
```

- [ ] **Step 4: Chạy test để xác nhận GREEN**

Run:

```powershell
.\mvnw.cmd -Dtest=WordRepositoryTest test
```

Expected: `Tests run: 3, Failures: 0, Errors: 0`.

---

### Task 2: Service regression coverage and full verification

**Files:**
- Modify: `src/test/java/com/bachdauduc/vocab_app/service/GetWordDataServiceTest.java`

**Interfaces:**
- Consumes: `GetWordDataService.searchWordObjectsByText(String, boolean, boolean)`.
- Produces: Test phòng vệ xác nhận Java tuple dedup giữ cả `Apple` và `apple` nhưng loại bản ghi trùng cùng case.

- [ ] **Step 1: Bổ sung test service cho khóa tuple case-sensitive**

Mock repository trả `Apple`, bản trùng `Apple`, `apple`, bản trùng `apple`; gọi autocomplete và assert response IDs chỉ gồm đại diện của hai cách viết.

- [ ] **Step 2: Chạy test tập trung**

Run:

```powershell
.\mvnw.cmd -Dtest=WordRepositoryTest,GetWordDataServiceTest test
```

Expected: toàn bộ repository và service tests pass.

- [ ] **Step 3: Chạy kiểm chứng toàn bộ**

Run:

```powershell
.\mvnw.cmd test
git diff --check
```

Expected: Maven `BUILD SUCCESS`, không có failure/error; `git diff --check` exit code 0.

- [ ] **Step 4: Rà phạm vi diff**

Xác nhận chỉ query DISTINCT, test hồi quy và tài liệu spec/plan thay đổi; không sửa contract API hoặc thay đổi ngoài scope.
