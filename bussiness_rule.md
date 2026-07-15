

---

# 1. Project Summary

This project is an English vocabulary and learning platform.

Main modules:

- Dictionary / vocabulary data
- IELTS reading content
- Listening exercises
- Listen-and-type challenges
- User saved vocabulary
- Vocabulary review / spaced repetition
- User answer attempts
- Daily and overall learning statistics
- User streak
- Push notification tokens
- Notification templates

Backend stack:

- Java 21
- Spring Boot 4
- Spring Web MVC
- Spring Data JPA
- MySQL 8.4
- JPA repositories are the current database access convention
- Redis/Kafka/Elasticsearch may be used later, but do not assume unless requested

Current API conventions:

- Controllers return `ApiResponse<T>`.
- Successful responses use code `2000`, `message`, `traceId`, and `result`.
- Business errors must throw `AppException` with an `ErrorCode`.
- `GlobalExceptionHandler` converts exceptions to `ApiResponse`.
- Do not expose `passwordHash` in any API response; use response DTOs such as `UserInfoResponse`.
- Auth public endpoints are `/auth/register`, `/auth/verify-email`, `/auth/forgot-password`, and `/auth/forgot-password/submit-code`.

Redis key rules:

- Store Redis key patterns in `src/main/resources/redis_keys.properties`.
- Access Redis key patterns through `RedisKeyProperties`.
- Current auth keys:
  - `pre_register_info:%s`
  - `pre_register_code:%s`
  - `forget_password_code:%s`
- Verification TTL is currently 5 minutes.

Database conventions:

- MySQL table/column names use `snake_case`
- Java fields use `camelCase`
- IDs are UUID strings stored as `VARCHAR(36)`
- Enum values are stored as `VARCHAR`
- Some DB columns are `TEXT` but contain JSON string values
- Avoid foreign keys unless explicitly requested
- Indexes should be created only when needed for the query patterns

---

# 2. General Output Rules for AI Assistant

When generating code:

1. Prefer complete, directly usable code.
2. Do not give vague pseudo-code unless explicitly requested.
3. Keep controller thin.
4. Put business logic in service classes.
5. Put database access in repository classes.
6. Use DTOs for request/response.
7. Do not expose database entities directly in API responses.
8. Use clear names.
9. Avoid unnecessary abstraction.
10. Explain only important decisions after code.
---

# 3. Entity Context

## 3.1 Dictionary Tables

## Table: `words`

Purpose: canonical dictionary word/phrase entries.

Fields:

| Column | Type rule | Meaning |
|---|---|---|
| `id` | `VARCHAR(36)` | UUID |
| `word` | `TEXT` | Original word or phrase |
| `normalized_word` | `TEXT` | Normalized word for lookup |
| `pos` | `VARCHAR(50)` | Part of speech |
| `lang` | `VARCHAR(50)` | Language name, e.g. `English` |
| `lang_code` | `VARCHAR(10)` | Language code, e.g. `en` |
| `word_source` | `VARCHAR(50)` | Source, e.g. `KAIKKI`, `MOCHI` |
| `other_source` | `VARCHAR(100)` | Optional extra source |
| `cert_level` | `VARCHAR(20)` | CEFR or level info |
| `created_at` | `TIMESTAMP` | Created time |
| `updated_at` | `TIMESTAMP` | Updated time |

Special rules:

- `word` and `normalized_word` may be longer than 255 characters.
- Use `TEXT` for them.
- For indexes on these columns, use prefix indexes such as `normalized_word(191)`.

---

## Table: `word_forms`

Purpose: stores inflected forms or alternative forms of a word.

Fields:

| Column | Type rule | Meaning |
|---|---|---|
| `id` | `VARCHAR(36)` | UUID |
| `word_id` | `VARCHAR(36)` | Reference to `words.id` |
| `form` | `TEXT` | Original form |
| `normalized_form` | `TEXT` | Normalized form |
| `tags` | `TEXT` JSON string | Form tags |

JSON string fields:

- `tags`

Example `tags` value:

```json
["plural"]
```

Special rules:

- `form` and `normalized_form` may exceed 255 characters.
- Use `TEXT`.

---

## Table: `word_sounds`

Purpose: pronunciation and audio for a word.

Fields:

| Column | Type rule | Meaning |
|---|---|---|
| `id` | `VARCHAR(36)` | UUID |
| `word_id` | `VARCHAR(36)` | Reference to `words.id` |
| `ipa` | `TEXT` or `VARCHAR(255)` | IPA pronunciation |
| `enpr` | `TEXT` | ENPR pronunciation |
| `tags` | `TEXT` JSON string | Pronunciation tags |
| `sound_source` | `VARCHAR(50)` | Source, e.g. `KAIKKI`, `MOCHI` |
| `ogg_url` | `TEXT` | OGG audio URL |
| `mp3_url` | `TEXT` | MP3 audio URL |

JSON string fields:

- `tags`

Example `tags` value:

```json
["UK"]
```

or:

```json
["US"]
```

Special rules:

- Do not log full audio URLs if not necessary.
- If generating update logic from URL, do not assume all URLs contain `uk` or `us`.

---

## Table: `word_senses`

Purpose: English definitions/senses for a word.

Fields:

| Column | Type rule | Meaning |
|---|---|---|
| `id` | `VARCHAR(36)` | UUID |
| `word_id` | `VARCHAR(36)` | Reference to `words.id` |
| `definition` | `TEXT` | English definition |
| `synonyms` | `TEXT` JSON string | Synonym list |
| `antonyms` | `TEXT` JSON string | Antonym list |
| `derived` | `TEXT` JSON string | Derived terms |
| `coordinate_terms` | `TEXT` JSON string | Coordinate terms |
| `form_of` | `TEXT` JSON string | Form-of relation |
| `alt_of` | `TEXT` JSON string | Alternative form relation |

JSON string fields:

- `synonyms`
- `antonyms`
- `derived`
- `coordinate_terms`
- `form_of`
- `alt_of`

Example:

```json
["reliable", "dependable"]
```

Special rules:

- Do not concatenate list data with commas.
- Use JSON string arrays.

---

## Table: `word_examples`

Purpose: example sentences for words/senses.

Fields:

| Column | Type rule | Meaning |
|---|---|---|
| `id` | `VARCHAR(36)` | UUID |
| `word_id` | `VARCHAR(36)` | Reference to `words.id` |
| `sense_id` | `VARCHAR(36)` nullable | Reference to `word_senses.id` |
| `text` | `TEXT` | Example sentence |
| `example_type` | `VARCHAR(50)` | Example type |
| `source_ref` | `TEXT` | Source reference |
| `created_at` | `TIMESTAMP` | Created time |
| `updated_at` | `TIMESTAMP` | Updated time |

Special rules:

- Column name `text` must be escaped in SQL if needed:
  ```sql
  `text`
  ```

---

## Table: `word_relations`

Purpose: relation data aggregated by word.

Fields:

| Column | Type rule | Meaning |
|---|---|---|
| `id` | `VARCHAR(36)` | UUID |
| `word_id` | `VARCHAR(36)` | Reference to `words.id` |
| `synonyms` | `TEXT` JSON string | Synonyms |
| `antonyms` | `TEXT` JSON string | Antonyms |
| `derived` | `TEXT` JSON string | Derived terms |
| `coordinate_terms` | `TEXT` JSON string | Coordinate terms |
| `form_of` | `TEXT` JSON string | Form-of relation |
| `alt_of` | `TEXT` JSON string | Alternative form relation |

JSON string fields:

- `synonyms`
- `antonyms`
- `derived`
- `coordinate_terms`
- `form_of`
- `alt_of`

---

## Table: `categories`

Purpose: dictionary category/topic data.

Fields:

| Column | Type rule | Meaning |
|---|---|---|
| `id` | `VARCHAR(36)` | UUID |
| `name` | `TEXT` or `VARCHAR(255)` | Category name |
| `slug` | `TEXT` or `VARCHAR(255)` | Category slug |
| `description` | `TEXT` | Description |
| `created_at` | `TIMESTAMP` | Created time |

---

## Table: `word_category`

Purpose: many-to-many mapping between words and categories.

Fields:

| Column | Type rule | Meaning |
|---|---|---|
| `id` | `VARCHAR(36)` | UUID |
| `word_id` | `VARCHAR(36)` | Reference to `words.id` |
| `category_id` | `VARCHAR(36)` | Reference to `categories.id` |

Recommended unique rule:

- `(word_id, category_id)` should be unique if duplicate mappings are not allowed.

---

## Table: `word_sense_localizations`

Purpose: localized meanings/translations for word senses.

Fields:

| Column | Type rule | Meaning |
|---|---|---|
| `id` | `VARCHAR(36)` | UUID |
| `sense_id` | `VARCHAR(36)` nullable | Reference to `word_senses.id` |
| `word_id` | `VARCHAR(36)` | Reference to `words.id` |
| `lang_code` | `VARCHAR(10)` | Target language, e.g. `vi` |
| `short_meaning` | `TEXT` | Short localized meaning |
| `full_localized_definition` | `TEXT` | Full localized definition |
| `source` | `VARCHAR(50)` | Source |
| `review_status` | `TINYINT` | Review status |
| `created_at` | `TIMESTAMP` | Created time |
| `updated_at` | `TIMESTAMP` | Updated time |

Special rules:

- `source` is a SQL-sensitive word in some contexts. Escape with backticks if needed:
  ```sql
  `source`
  ```

---

## Table: `word_example_localizations`

Purpose: translations of example sentences.

Fields:

| Column | Type rule | Meaning |
|---|---|---|
| `id` | `VARCHAR(36)` | UUID |
| `example_id` | `VARCHAR(36)` | Reference to `word_examples.id` |
| `word_id` | `VARCHAR(36)` | Reference to `words.id` |
| `sense_id` | `VARCHAR(36)` nullable | Reference to `word_senses.id` |
| `lang_code` | `VARCHAR(10)` | Target language |
| `translated_text` | `TEXT` | Translated example |
| `review_status` | `TINYINT` | Review status |
| `created_at` | `TIMESTAMP` | Created time |
| `updated_at` | `TIMESTAMP` | Updated time |

---

## Table: `word_idioms`

Purpose: idioms or phrasal expressions associated with a word/sense.

Fields:

| Column | Type rule | Meaning |
|---|---|---|
| `id` | `VARCHAR(36)` | UUID |
| `word_id` | `VARCHAR(36)` | Reference to `words.id` |
| `sense_id` | `VARCHAR(36)` nullable | Reference to `word_senses.id` |
| `idiom` | `TEXT` | Idiom text |
| `definition` | `TEXT` | Definition |
| `definition_gpt` | `TEXT` | GPT-generated definition |
| `idiom_source` | `VARCHAR(50)` | Idiom source |
| `example` | `TEXT` | Example |
| `example2` | `TEXT` | Second example |
| `created_at` | `TIMESTAMP` | Created time |
| `updated_at` | `TIMESTAMP` | Updated time |

---

## Table: `word_idiom_trans`

Purpose: localized translations of idioms.

Fields:

| Column | Type rule | Meaning |
|---|---|---|
| `id` | `VARCHAR(36)` | UUID |
| `idiom_id` | `VARCHAR(36)` | Reference to `word_idioms.id` |
| `idiom` | `TEXT` | Localized idiom if available |
| `definition` | `TEXT` | Translated definition |
| `definition_gpt` | `TEXT` | GPT-generated translated definition |
| `example` | `TEXT` | Translated example |
| `example_2` | `TEXT` | Second translated example |
| `review_status` | `TINYINT` | Review status |
| `lang_code` | `VARCHAR(10)` | Target language |
| `created_at` | `TIMESTAMP` | Created time |
| `updated_at` | `TIMESTAMP` | Updated time |

---

# 4. Exercise Tables

## Table: `ielts_reading_sources`

Purpose: IELTS reading content extracted from sources.

Fields:

| Column | Type rule | Meaning |
|---|---|---|
| `id` | `VARCHAR(36)` | UUID |
| `name` | `VARCHAR(255)` | Source/name |
| `title` | `VARCHAR(500)` | Title |
| `category_id` | `VARCHAR(36)` nullable | Category id |
| `content` | `LONGTEXT` | Full reading content |

Special rules:

- `content` may be very large.
- Do not log full `content`.
- Log only length or short preview.

---

## Table: `listening_category`

Purpose: listening categories.

Fields:

| Column | Type rule | Meaning |
|---|---|---|
| `id` | `VARCHAR(36)` | UUID |
| `category_name` | `VARCHAR(255)` | Category name |
| `slug` | `VARCHAR(255)` | Slug |
| `description` | `TEXT` | Description |

---

## Table: `listen_exercise`

Purpose: listening lesson/exercise.

Fields:

| Column | Type rule | Meaning |
|---|---|---|
| `lesson_id` | `VARCHAR(36)` | Primary id |
| `title` | `VARCHAR(500)` | Title |
| `category_id` | `VARCHAR(36)` nullable | Category id |
| `full_document` | `LONGTEXT` | Full transcript/document |
| `speech_to_text_lang_code` | `VARCHAR(20)` | Language code |
| `audio_url` | `TEXT` | Audio URL |
| `learning_resource_type` | `VARCHAR(100)` | Resource type |
| `created_at` | `TIMESTAMP` | Created time |
| `updated_at` | `TIMESTAMP` | Updated time |

Special rules:

- Do not log full `full_document`.
- Log `lesson_id`, `title`, `audio_url` only if safe, and content length.

---

## Table: `listen_and_type_exercise_challenges`

Purpose: individual listen-and-type challenge items.

Fields:

| Column | Type rule | Meaning |
|---|---|---|
| `id` | `VARCHAR(36)` | UUID |
| `listen_exercise_id` | `VARCHAR(36)` | Reference to `listen_exercise.lesson_id` |
| `position` | `INT` | Challenge order |
| `content` | `LONGTEXT` | Challenge content |
| `audio_src` | `TEXT` | Challenge audio source |
| `json_content` | `LONGTEXT` JSON string | Structured challenge data |
| `solution` | `LONGTEXT` | Correct answer/solution |
| `time_start` | `DECIMAL(10,3)` | Audio start time |
| `time_end` | `DECIMAL(10,3)` | Audio end time |
| `hints` | `LONGTEXT` JSON string | Hints |

JSON string fields:

- `json_content`
- `hints`

Special rules:

- Validate `time_start <= time_end` when both values are present.
- Do not expose `solution` in APIs that serve questions to users.
- Do not log full `solution`, `content`, or `json_content`.

Expected TSV order:

```text
id	listen_exercise_id	position	content	audio_src	json_content	solution	time_start	time_end	hints
```

---

# 5. User & Learning Tables

## Table: `user_info`

Purpose: application user account data.

Fields:

| Column | Type rule | Meaning |
|---|---|---|
| `id` | `VARCHAR(36)` | UUID |
| `username` | `VARCHAR(100)` | Username |
| `password_hash` | `TEXT` | Password hash |
| `email` | `VARCHAR(255)` | Email |
| `provider_name` | `VARCHAR(100)` | Login provider |
| `provider_id` | `VARCHAR(255)` | Provider id |
| `created_at` | `TIMESTAMP` | Created time |
| `updated_at` | `TIMESTAMP` | Updated time |

Security rules:

- Do not store raw password.
- Do not log `password_hash`.
- Do not return `password_hash` in API responses.

---

## Table: `user_search_history`

Purpose: history of searched words by user.

Fields:

| Column | Type rule | Meaning |
|---|---|---|
| `id` | `VARCHAR(36)` | UUID |
| `user_id` | `VARCHAR(36)` | User id |
| `word_id` | `VARCHAR(36)` | Word id |
| `searched_at` | `TIMESTAMP` | Search time |

---

## Table: `user_vocabularies`

Purpose: words saved by users for review.

Fields:

| Column | Type rule | Meaning |
|---|---|---|
| `id` | `VARCHAR(36)` | UUID |
| `user_id` | `VARCHAR(36)` | User id |
| `word_id` | `VARCHAR(36)` | Word id |
| `sense_id` | `VARCHAR(36)` | Selected word sense id |
| `saved_at` | `TIMESTAMP` | Saved time |
| `level` | `INT` | Review level |
| `next_review_at` | `TIMESTAMP NULL` | Next review time |
| `created_at` | `TIMESTAMP` | Created time |
| `updated_at` | `TIMESTAMP` | Updated time |

Business rules:

- `level` should be between 1 and 6 unless user changes the review model.
- `next_review_at` determines whether vocabulary is due for review.
- Use `next_review_at <= now` to find due vocabularies.
- Do not create another boolean column like `is_next_review` unless necessary.

---

## Table: `user_vocab_attempts`

Purpose: every answer attempt from a user.

Fields:

| Column | Type rule | Meaning |
|---|---|---|
| `id` | `VARCHAR(36)` | UUID |
| `attempt_id` | `VARCHAR(36)` | Attempt/session id |
| `user_id` | `VARCHAR(36)` | User id |
| `user_vocab_id` | `VARCHAR(36)` | Reference to `user_vocabularies.id` |
| `exercise_type` | `VARCHAR(100)` | Exercise type enum |
| `user_answer` | `TEXT` | User answer |
| `is_correct` | `BOOLEAN` | Correctness |
| `replay_count` | `INT` | Audio replay count |
| `created_at` | `TIMESTAMP` | Attempt time |

Business rules:

- Backend should calculate `is_correct`; do not blindly trust client.
- Keep raw `user_answer` for debug/analytics if safe.
- Avoid logging full `user_answer` if it may contain personal information.

---

## Table: `user_daily_vocab_statistics`

Purpose: daily aggregated review statistics.

Fields:

| Column | Type rule | Meaning |
|---|---|---|
| `id` | `VARCHAR(36)` | UUID |
| `user_id` | `VARCHAR(36)` | User id |
| `statistic_date` | `DATE` | Date |
| `total_attempts` | `INT` | Total attempts |
| `correct_attempts` | `INT` | Correct attempts |
| `wrong_attempts` | `INT` | Wrong attempts |
| `accuracy` | `DECIMAL(5,2)` | Correct ratio percent |
| `total_unique_vocab` | `INT` | Unique vocab count |
| `correct_unique_vocab` | `INT` | Unique correct vocab count |
| `wrong_unique_vocab` | `INT` | Unique wrong vocab count |
| `wrong_vocab_ids` | `TEXT` JSON string | Wrong vocab ids |
| `new_wrong_vocab_ids` | `TEXT` JSON string | Newly wrong vocab ids |
| `repeated_wrong_vocab_ids` | `TEXT` JSON string | Repeated wrong vocab ids |
| `most_wrong_vocab_ids` | `TEXT` JSON string | Most wrong vocab ids |
| `created_at` | `TIMESTAMP` | Created time |
| `updated_at` | `TIMESTAMP` | Updated time |

JSON string fields:

- `wrong_vocab_ids`
- `new_wrong_vocab_ids`
- `repeated_wrong_vocab_ids`
- `most_wrong_vocab_ids`

Business rules:

- One row per `user_id + statistic_date`.
- `accuracy = correct_attempts / total_attempts * 100`.
- Backend calculates all statistics.

---

## Table: `user_overall_vocab_statistics`

Purpose: all-time aggregated review statistics.

Fields:

| Column | Type rule | Meaning |
|---|---|---|
| `id` | `VARCHAR(36)` | UUID |
| `user_id` | `VARCHAR(36)` | User id |
| `total_attempts` | `INT` | Total attempts |
| `correct_attempts` | `INT` | Correct attempts |
| `wrong_attempts` | `INT` | Wrong attempts |
| `accuracy` | `DECIMAL(5,2)` | Correct ratio percent |
| `total_unique_vocab` | `INT` | Unique vocab count |
| `correct_unique_vocab` | `INT` | Unique correct vocab count |
| `wrong_unique_vocab` | `INT` | Unique wrong vocab count |
| `wrong_vocab_ids` | `TEXT` JSON string | Wrong vocab ids |
| `wrong_by_exercise_type` | `TEXT` JSON string | Wrong count by exercise type |
| `most_wrong_vocab_ids` | `TEXT` JSON string | Most wrong vocab ids |
| `created_at` | `TIMESTAMP` | Created time |
| `updated_at` | `TIMESTAMP` | Updated time |

JSON string fields:

- `wrong_vocab_ids`
- `wrong_by_exercise_type`
- `most_wrong_vocab_ids`

Example `wrong_by_exercise_type`:

```json
{
  "WORD_TO_MEANING": 12,
  "MEANING_TO_WORD": 8,
  "LISTEN_AND_TYPE_WORD": 5
}
```

Business rules:

- One row per user.
- Backend calculates all statistics.

---

## Table: `user_streak`

Purpose: user learning streak.

Fields:

| Column | Type rule | Meaning |
|---|---|---|
| `user_id` | `VARCHAR(36)` | Primary key |
| `current_streak` | `INT` | Current streak |
| `created_at` | `TIMESTAMP` | Created time |
| `updated_at` | `TIMESTAMP` | Updated time |

---

# 6. Notification Tables

## Table: `user_push_tokens`

Purpose: store user push tokens for sending push notifications.

Fields:

| Column | Type rule | Meaning |
|---|---|---|
| `id` | `VARCHAR(36)` | UUID |
| `user_id` | `VARCHAR(36)` | User id |
| `device_id` | `VARCHAR(255)` | Device id |
| `device_name` | `VARCHAR(255)` | Device name |
| `push_token` | `TEXT` | Full push token |
| `push_token_hash` | `CHAR(64)` | SHA-256 hash of token |
| `platform` | `VARCHAR(50)` | `WEB`, `ANDROID`, `IOS` |
| `provider` | `VARCHAR(50)` | `FCM`, `APNS`, `WEB_PUSH` |
| `is_active` | `BOOLEAN` | Token active flag |
| `last_seen_at` | `TIMESTAMP NULL` | Last token refresh |
| `created_at` | `TIMESTAMP` | Created time |
| `updated_at` | `TIMESTAMP` | Updated time |

Security rules:

- Do not log full `push_token`.
- Log `push_token_hash` instead.
- If provider says token is invalid, set `is_active = false`.

---

## Table: `notification_templates`

Purpose: notification text templates by user action type.

Fields:

| Column | Type rule | Meaning |
|---|---|---|
| `id` | `INT AUTO_INCREMENT` | Primary key |
| `action_type` | `VARCHAR(255)` | Java enum `UserActionType` |
| `template` | `TEXT` | Template content |
| `created_at` | `TIMESTAMP` | Created time |
| `updated_at` | `TIMESTAMP` | Updated time |

Business rules:

- `action_type` should be unique.
- Template may contain placeholders.
- Example:
  ```text
  {{actorName}} đã thích bài viết của bạn
  ```

---

# 9. Logging Rules

## 9.1 General logging

Use logs at important input/output points.

Log:

- request identifiers
- user id
- entity id
- action type
- count of loaded/updated records
- state transition
- timing if useful

Do not log:

- raw password
- password hash
- full push token
- access token
- refresh token
- cookie
- full long text content
- full solution if it should be hidden from user

---

## 9.2 Log levels

Use:

- `DEBUG`: technical details useful during development
- `INFO`: successful business events
- `WARN`: suspicious but recoverable situations
- `ERROR`: failed request/job

---

## 9.3 Controller logs

At controller input:

```java
log.info("Request received: action={}, userId={}, resourceId={}",
        action, userId, resourceId);
```

At controller output:

```java
log.info("Request completed: action={}, userId={}, status={}",
        action, userId, status);
```

Do not log entire request body if it contains large text or sensitive data.

---

## 9.4 Service logs

At service input:

```java
log.debug("Start service: method={}, userId={}, targetId={}",
        methodName, userId, targetId);
```

At important state change:

```java
log.info("Vocabulary review updated: userId={}, userVocabId={}, oldLevel={}, newLevel={}, isCorrect={}, nextReviewAt={}",
        userId, userVocabId, oldLevel, newLevel, isCorrect, nextReviewAt);
```

At service output:

```java
log.debug("Service completed: method={}, userId={}, resultCount={}",
        methodName, userId, resultCount);
```

---

## 9.5 Repository logs

Do not log every query result row.

Log counts:

```java
log.debug("Due vocab loaded: userId={}, limit={}, resultCount={}",
        userId, limit, resultCount);
```

Log update summaries:

```java
log.info("Statistics updated: userId={}, date={}, totalAttempts={}, accuracy={}",
        userId, date, totalAttempts, accuracy);
```

---

## 9.6 JSON parsing logs

When parsing a JSON string field:

```java
log.debug("Parse JSON field: table={}, field={}, rowId={}, length={}",
        tableName, fieldName, rowId, json == null ? 0 : json.length());
```

When parse fails:

```java
log.warn("JSON parse failed: table={}, field={}, rowId={}, length={}",
        tableName, fieldName, rowId, json == null ? 0 : json.length(), ex);
```

Do not log full JSON if it is large.

---

# 10. Comment Rules

Do not comment obvious code.

Bad:

```java
// set user id
entity.setUserId(userId);
```

Good:

```java
// Keep mastered vocabulary at level 6 after the first wrong answer to avoid over-penalizing occasional mistakes.
```

Comment when explaining:

- non-obvious business rule
- review interval calculation
- level up/down logic
- JSON string conversion
- security masking
- notification template rendering
- data migration/import workaround

TODO format:

```java
// TODO(bachdd): Move dictionary autocomplete to Elasticsearch when MySQL search becomes slow.
```

Avoid:

```java
// TODO fix later
```

---
