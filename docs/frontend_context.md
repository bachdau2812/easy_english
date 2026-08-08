# Frontend Context For Vocab App Backend

This document was generated from the current backend code in `src/main/java/com/bachdauduc/vocab_app`, `src/main/resources/application.properties`, `src/main/resources/redis_keys.properties`, and `bussiness_rule.md`.

Do not treat business-rule-only tables as implemented APIs unless they are listed in the endpoint section below. If something is not visible in current backend code, it is marked as `Unknown from current backend code`.

## 1. Project Overview

The backend is a Spring Boot vocabulary learning API for:

| Area | Current backend support |
|---|---|
| Authentication | register, verify email, login, logout, refresh token, reset/forgot password |
| User profile | get user info, update username |
| Dictionary data | word detail, senses, examples, idioms, forms, relations, sounds, text search |
| Vocabulary saving | save a word sense/localized sense, review scheduling, attempts, history |
| Review exercises | generate vocabulary review quizzes and submit attempts |
| Listen-and-type | categories, lesson list, lesson detail, progress, submit listen challenge attempt |
| Learning resources | IELTS reading source list/detail quiz retrieval; IELTS writing topics/problems/references/review; some insert/generate endpoints are placeholders |
| Notifications | send email/push notification through templates |
| Streak | Entity/API not implemented in current backend code |
| Push token registration | Entity exists, but registration API not implemented in current backend code |

Backend facts:

| Item | Value |
|---|---|
| Java | 21 |
| Spring Boot | 4.1.0 |
| Server port | `8080` |
| Context path | `/vocab-learning` |
| Local base URL | `http://localhost:8080/vocab-learning` |
| Database | MySQL, database name `vocab_app` |
| Cache | Redis |
| Groq API key | `GROQ_API_KEY` through `groq.api.key`; legacy `GROK_API_KEY` remains a fallback; must be set in the environment of the running Spring Boot process before startup |
| Auth | JWT Bearer token, HS512 signed |
| CORS | Only `http://localhost:5173`, credentials allowed |
| Timezone config | `Asia/Ho_Chi_Minh` |

## 2. Auth, Session, Cookie, And Token Behavior

There is no cookie/session login behavior in current backend code. Authentication uses JWT in response bodies and Spring OAuth2 Resource Server validation.

Frontend should:

1. Call `POST /auth/login`.
2. Store `result.token` client-side.
3. Send authenticated requests with `Authorization: Bearer <token>`.
4. Use `result.userId` and `result.username` from login for local session state.
5. Restore a session by reusing the stored token and calling `GET /users/info?userId=...`.

Public endpoints from `SecurityConfig`:

| Endpoint | Auth required |
|---|---|
| `POST /auth/register` | No |
| `POST /auth/verify-email` | No |
| `POST /auth/login` | No |
| `POST /auth/logout` | No by security config, but body token is required |
| `POST /auth/refresh-token` | No by security config, but body token is required |
| `POST /auth/forgot-password` | No |
| `POST /auth/forgot-password/submit-code` | No |
| `GET /word-data/word` | No |
| `GET /word-data/words/search` | No |
| `GET /word-data/words/basic-search` | No |
| `GET /word-data/words/by-category` | No |
| `GET /word-data/words/by-level` | No |
| `GET /word-data/categories` | No |
| `GET /learning-resources/ielts-reading-sources` | No |
| `GET /learning-resources/ielts-reading-sources/categories` | No |
| `GET /learning-resources/ielts-reading-sources/by-category` | No |
| `GET /exercises/listen-and-type/categories` | No |
| `GET /exercises/listen-and-type/sub-categories` | No |
| `GET /exercises/listen-and-type/lessons` | No |
| Swagger endpoints | No |
| All other endpoints | Yes |

JWT behavior from code:

| Behavior | Current implementation |
|---|---|
| Login response | `AuthenticationResponse { token, userId, username }` |
| Refresh response | `AuthenticationResponse { token }`; `userId` and `username` are not set |
| Logout | Saves Redis key `logout:<token>` with TTL 1 day |
| Token validity | Custom decoder checks signature, expiry, and Redis logout key |
| Token duration | `${jwt.valid-duration}` seconds; current config `129600` |
| Token subject | `UserInfo.id` |
| Token scope | `ROLE_` + `userRole` |
| Cookies | Unknown from current backend code; no cookie-writing code found |

Sensitive handling:

- Do not send or display password hashes.
- Do not log JWTs.
- Do not render listen challenge `solution` to the user even though current backend returns it.

## 3. Global API Response And Error Conventions

All controllers return `ApiResponse<T>`.

```json
{
  "code": 2000,
  "message": "Get word data successfully",
  "traceId": "a-trace-id",
  "result": {}
}
```

`traceId` is generated or accepted from the `X-Trace-Id` header. The backend also echoes it as response header `X-Trace-Id`.

Validation errors:

```json
{
  "code": 8888,
  "message": "username: must not be blank",
  "traceId": "a-trace-id",
  "result": null
}
```

Unauthenticated response from security:

```json
{
  "code": 1001,
  "message": "Unauthenticated",
  "traceId": null,
  "result": null
}
```

Error codes:

| Code | Message | HTTP status |
|---:|---|---|
| 1001 | `Unauthenticated` | 401 |
| 2001 | `Username already exists` | 400 |
| 2002 | `Email already exists` | 400 |
| 2003 | `User not found` | 404 |
| 2004 | `Email not found` | 404 |
| 2005 | `Register information expired` | 400 |
| 2006 | `Invalid verification code` | 400 |
| 2007 | `Invalid password` | 400 |
| 2008 | `Invalid token` | 401 |
| 2009 | `Word not found` | 404 |
| 2010 | `Category not found` | 404 |
| 2011 | `Word example not found` | 404 |
| 2012 | `Word idiom not found` | 404 |
| 2013 | `Translation failed` | 400 |
| 2014 | `User vocabulary not found` | 404 |
| 2015 | `Listen and type challenge not found` | 404 |
| 2016 | `Invalid exercise type` | 400 |
| 2017 | `Invalid user vocabulary request` | 400 |
| 2018 | `Review vocab total must be 30, 60, or 90` | 400 |
| 2019 | `All vocab exercise types were generated for this review session` | 400 |
| 2020 | `Word sound not found` | 404 |
| 2021 | `Lesson not found` | 404 |
| 2022 | `Invalid lesson type` | 400 |
| 2023 | `User vocabulary already exists`; current duplicate save messages are `wordId + senseId already exists` or `wordId + senseLocalizedId already exists` | 400 |
| 3001 | `Notification template not found` | 404 |
| 3002 | `Unsupported notification method` | 400 |
| 3003 | `Push token not found` | 404 |
| 3004 | `Notification send failed` | 400 |
| 9999 | Unhandled exception message | 400 in current handler |

Spring `Page<T>` responses are returned inside `result` with normal Spring page fields such as `content`, `pageable`, `totalElements`, `totalPages`, `number`, `size`, `first`, `last`, and `numberOfElements`.

## 4. Entity Summary With Frontend-Relevant Fields

Only frontend-relevant fields are listed. Sensitive fields such as `passwordHash` and `pushToken` should not be used in public frontend models.

| Entity/table | Frontend-relevant fields |
|---|---|
| `UserInfo` / `user_info` | `id`, `username`, `email`, `userRole`, `createdAt`, `updatedAt` |
| `Word` / `words` | `id`, `word`, `normalizedWord`, `pos`, `lang`, `langCode`, `wordSource`, `otherSource`, `certLevel`, `createdAt`, `updatedAt` |
| `Category` / `categories` | `id`, `name`, `slug`, `description`, `createdAt` |
| `WordCategory` / `word_category` | `id`, `wordId`, `categoryId`, `createdAt`, `updatedAt` |
| `WordSense` / `word_senses` | `id`, `wordId`, `definition`, relation JSON fields |
| `WordSenseLocalization` / `word_sense_localizations` | `id`, `senseId`, `wordId`, `langCode`, `shortMeaning`, `fullLocalizedDefinition`, `source`, `reviewStatus` |
| `WordExample` / `word_examples` | `id`, `wordId`, `senseId`, `text`, `exampleType`, `sourceRef` |
| `WordExampleLocalization` / `word_example_localizations` | `id`, `exampleId`, `wordId`, `senseId`, `langCode`, `translatedText`, `reviewStatus` |
| `WordSound` / `word_sounds` | `id`, `wordId`, `ipa`, `tags`, `soundSource`, `oggUrl`, `mp3Url`, `enpr` |
| `WordForm` / `word_forms` | `id`, `wordId`, `form`, `normalizedForm`, `tags` |
| `WordRelation` / `word_relations` | `id`, `wordId`, relation JSON fields |
| `WordIdiom` / `word_idioms` | `id`, `wordId`, `senseId`, `idiom`, `definition`, `definitionGpt`, `example`, `example2`, `idiomSource` |
| `WordIdiomTranslation` / `word_idiom_trans` | `id`, `idiomId`, `idiom`, `definition`, `definitionGpt`, `example`, `example2`, `reviewStatus`, `langCode` |
| `UserVocabulary` / `user_vocabularies` | `id`, `userId`, `wordId`, `senseId`, `senseLocalizedId`, `level`, `currentLevelCorrectTurns`, `nextReviewAt`, `createdAt`, `updatedAt` |
| `UserVocabAttempt` / `user_vocab_attempts` | `id`, `attemptId`, `userId`, `userVocabId`, `exerciseType`, `userAnswer`, `correct`, `replayCount`, `createdAt` |
| `UserSearchHistory` / `user_search_history` | `id`, `userId`, `wordId`, `searchedAt` |
| `UserLesson` / `user_lesson` | `id`, `userId`, `lessonId`, `lessonType`, `createdAt`, `updatedAt` |
| `ListeningCategory` / `listening_category` | `id`, `categoryName`, `slug`, `description` |
| `ListenAndTypeSubCategory` / `listen_and_type_sub_category` | `id`, `categoryId`, `subCategoryName` |
| `ListenExercise` / `listen_exercise` | `lessonId`, `title`, `categoryId`, `subCategory`, `fullDocument`, `speechToTextLangCode`, `audioUrl`, `learningResourceType` |
| `ListenAndTypeExerciseChallenge` / `listen_and_type_exercise_challenges` | `id`, `listenExerciseId`, `position`, `content`, `jsonContent`, `timeStart`, `timeEnd`, `hints`, `audioSrc`; backend currently also returns `solution` |
| `IeltsReadingSource` / `ielts_reading_sources` | `id`, `name`, `title`, `categoryId`, `content`, `createdAt`, `updatedAt` |
| `IeltsReadingQuizGroup` / `ielts_reading_quiz_groups` | `id`, `readingSourceId`, `quizType`, `groupOrder`, `instruction`, `questionNumberStart`, `questionNumberEnd`, `wordLimit`, `sourceParagraphId`, `sharedOptions` |
| `IeltsReadingQuestion` / `ielts_reading_questions` | `id`, `groupId`, `readingSourceId`, `questionNumber`, `stem`, `options`, `answer`, `sourceParagraphId`, `evidenceQuote`, `explanation`, `createdAt` |
| `IeltsWritingExercise` / `ielts_writing_exercise` | `id`, `problem`, `problemTopic`, `taskType`, `evaluationPrompt`, `imageUrl`, `imageDescription`, `createdAt`, `updatedAt` |
| `IeltsWritingReference` / `ielts_writing_reference` | `id`, `ieltsWritingExerciseId`, `essay`, `band`, `createdAt`, `updatedAt` |
| `NotificationTemplate` / `notification_templates` | `id`, `actionType`, `template` |
| `UserPushToken` / `user_push_tokens` | Entity exists with `id`, `userId`, `deviceName`, `createdAt`; no public response DTO/API found |

## 5. JSON String Fields

These fields are stored as JSON strings in DB. Response DTOs usually parse them to arrays, but some endpoints return raw entities.

| DB field | Returned as parsed frontend value? | Notes |
|---|---|---|
| `word_forms.tags` | Yes in `WordFormResponse.tags: string[]` | Raw entity endpoint can return string |
| `word_sounds.tags` | Yes in `WordSoundResponse.tags: string[]` | Raw entity endpoint can return string |
| `word_senses.synonyms`, `antonyms`, `derived`, `coordinate_terms`, `form_of`, `alt_of` | Yes in `WordSenseResponse` | Parse errors become empty list through `RedisUtil.deserializeList` behavior |
| `word_relations.synonyms`, `antonyms`, `derived`, `coordinate_terms`, `form_of`, `alt_of` | Yes in `WordRelationResponse` | Same list fields |
| `listen_and_type_exercise_challenges.json_content` | No | Returned as `jsonContent: string`; frontend should parse defensively |
| `listen_and_type_exercise_challenges.hints` | No | Returned as `hints: string`; frontend should parse defensively |

Frontend guidance:

- Treat `jsonContent` and `hints` as opaque strings unless valid JSON parsing succeeds.
- `Word` objects from `/word-data/words/basic-search` are raw entities, so no related JSON parsing/mapping is done.
- Do not split JSON arrays by comma. Use `JSON.parse` when the backend returns a raw JSON string.

## 6. Enum Values And Usage

`ExerciseType` values:

| Value | Used by |
|---|---|
| `VOCAB_WORD_TO_MEANING` | Vocab review quiz and attempt submit |
| `VOCAB_FILL_MISSING_WORD_PART` | Vocab review quiz and attempt submit |
| `VOCAB_LISTEN_AND_TYPE_WORD` | Vocab review quiz and attempt submit |
| `VOCAB_CHOOSE_WORD_IN_SENTENCE_BLANK` | Vocab review quiz and attempt submit |
| `VOCAB_FILL_WORD_IN_SENTENCE_BLANK` | Vocab review quiz and attempt submit |
| `VOCAB_MEANING_TO_SOUND` | Vocab review quiz and attempt submit |
| `VOCAB_SENTENCE_TO_MEANING` | Vocab review quiz and attempt submit |
| `VOCAB_SENTENCE_BLANK_TO_SOUND` | Vocab review quiz and attempt submit |
| `LAT_LISTEN_AND_TYPE` | Listen-and-type challenge attempt submit/progress |

Lesson type accepted by service:

| User input | Normalized |
|---|---|
| `LISTEN_AND_TYPE` | `LISTEN_AND_TYPE` |
| `listen-and-type` | `LISTEN_AND_TYPE` |
| `LAT` | `LISTEN_AND_TYPE` |

Notification method strings:

| Value | Behavior |
|---|---|
| `EMAIL` | Sends to `recipientId` as email address |
| `PUSH` | Sends push notification by user id through push-token lookup |

Notification type strings are not enum-backed in current code. They must match rows in `notification_templates.action_type`. Known types used by code: `PRE_REGISTER`, `WELCOME_USER`, `FORGET_PASSWORD`, `NEW_PASSWORD`.

## 7. Full Endpoint Documentation

All paths below are relative to `/vocab-learning`.

### Auth Endpoints

| Method | Path | Auth | Request | Response result | Common errors | Frontend notes |
|---|---|---|---|---|---|---|
| POST | `/auth/register` | No | `RegisterUserRequest` | `string` | `USERNAME_ALREADY_EXISTS`, `EMAIL_ALREADY_EXISTS`, validation | Sends verification code to email; user is not created yet |
| POST | `/auth/verify-email` | No | `VerifyEmailRequest` | `UserInfoResponse` | `REGISTER_INFORMATION_EXPIRED`, `INVALID_CODE`, duplicate username/email | Body contains only `email` and `code` |
| POST | `/auth/login` | No | `LoginRequest` | `AuthenticationResponse` | `USER_NOT_FOUND`, `INVALID_PASSWORD` | Save `token`, `userId`, `username` |
| POST | `/auth/logout` | No by security config | `LogoutRequest` | `string` | validation | Stores token in Redis blacklist for 1 day |
| POST | `/auth/refresh-token` | No by security config | `RefreshTokenRequest` | `AuthenticationResponse` with `token` only | `INVALID_TOKEN` | Backend does not currently return `userId`/`username` here |
| POST | `/auth/reset-password` | Yes | `ResetPasswordRequest` | `string` | `USER_NOT_FOUND`, `INVALID_PASSWORD` | Requires old password |
| POST | `/auth/forgot-password` | No | `ForgetPasswordRequest` | `string` | `EMAIL_NOT_FOUND` | Sends code to email |
| POST | `/auth/forgot-password/submit-code` | No | `VerifyEmailRequest` | `string` | `EMAIL_NOT_FOUND`, `REGISTER_INFORMATION_EXPIRED`, `INVALID_CODE` | Backend generates a new password and emails it |

Request bodies:

```json
{ "username": "bach", "password": "secret", "email": "bach@example.com" }
```

```json
{ "email": "bach@example.com", "code": "123456" }
```

```json
{ "username": "bach", "password": "secret" }
```

```json
{ "token": "jwt-token" }
```

```json
{ "userId": "uuid", "oldPassword": "old", "newPassword": "new" }
```

### User Endpoints

| Method | Path | Auth | Query/body | Response result | Common errors | Frontend notes |
|---|---|---|---|---|---|---|
| GET | `/users/info` | Yes | Query `userId` | `UserInfoResponse` | `USER_NOT_FOUND` | Checks Redis key `user_info:<userId>` before DB |
| PUT | `/users/info` | Yes | `UpdateUserInfoRequest` | `UserInfoResponse` | `USER_NOT_FOUND`, `USERNAME_ALREADY_EXISTS`, validation | Only username can be changed |

```json
{ "userId": "uuid", "username": "newName" }
```

### Word Data Endpoints

| Method | Path | Auth | Query params | Response result | Common errors | Frontend notes |
|---|---|---|---|---|---|---|
| GET | `/word-data/word` | No | `wordId`, `isTrans=false`, `transLangCode?`, `userId?` | `WordResponse` | `WORD_NOT_FOUND`, `USER_NOT_FOUND`, `TRANSLATION_FAILED` | If `userId` is passed, inserts/refreshes search history |
| GET | `/word-data/words/search` | No | `text`, `isTrans=false`, `transLangCode?` | `WordResponse[]` | `WORD_NOT_FOUND`, `TRANSLATION_FAILED` | Searches exact `normalizedWord`, not `word`; returns full mapped word detail |
| GET | `/word-data/words/basic-search` | No | `text`, `isAutocomplete=false` | `Word[]` raw entity | None specific beyond auth | Searches exact `normalizedWord` when false; searches `normalizedWord` prefix when true; no related data mapping |
| GET | `/word-data/words/basic-search/by-category` | No | `text`, `categoryId`, `isAutocomplete=false`, `page=0`, `limit=20` | `Page<Word>` raw entity | `CATEGORY_NOT_FOUND` | Searches exact/prefix `normalizedWord` inside one category; no related data mapping |
| GET | `/word-data/words/basic-search/by-level` | No | `text`, `level`, `isAutocomplete=false`, `page=0`, `limit=20` | `Page<Word>` raw entity | Auth errors | Searches exact/prefix `normalizedWord` inside one CEFR level; `level` is normalized to uppercase like `A1`-`C2` |
| GET | `/word-data/words/by-category` | No | `categoryId`, `page=0`, `limit=20` | `Page<Word>` raw entity | `CATEGORY_NOT_FOUND` | Returns words mapped through `word_category` |
| GET | `/word-data/words/by-level` | No | `level`, `page=0`, `limit=20` | `Page<Word>` raw entity | Auth errors | Filters `words.cert_level` by exact value |
| GET | `/word-data/categories` | No | None | `Category[]` raw entity | Auth errors | Vocabulary categories sorted by `name` |
| GET | `/word-data/senses` | Yes | `wordId`, `isTrans=false`, `transLangCode?` | `WordSenseResponse[]` | `WORD_NOT_FOUND`, `TRANSLATION_FAILED` | For MOCHI, reads `word_sense_localizations` with `sense_id IS NULL` |
| GET | `/word-data/examples` | Yes | `wordId`, `isTrans=false`, `transLangCode?` | `WordExampleResponse[]` | `WORD_NOT_FOUND`, `TRANSLATION_FAILED` | Examples are sense-specific |
| GET | `/word-data/idioms` | Yes | `wordId`, `isTrans=false`, `transLangCode?` | `WordIdiomResponse[]` | `WORD_NOT_FOUND` | `trans` present only when translation exists |
| GET | `/word-data/forms` | Yes | `wordId` | `WordFormResponse[]` | `WORD_NOT_FOUND` | Tags are parsed array |
| GET | `/word-data/relations` | Yes | `wordId` | `WordRelationResponse[]` | `WORD_NOT_FOUND` | Relation list fields are parsed arrays |

Word detail cache keys:

| Request | Redis key |
|---|---|
| `isTrans=true` | `word_with_trans:<wordId>` |
| `isTrans=false` | `word_without_trans:<wordId>` |

Important cache caveat: cache keys do not include `transLangCode`, so different translation languages for the same word can collide in current backend behavior.

MOCHI mapping:

- If `Word.otherSource == "MOCHI"`, senses are loaded from `word_sense_localizations` where `sense_id IS NULL` and `source = 'MOCHI'`.
- MOCHI examples map `words -> word_sense_localizations -> word_examples -> word_example_localizations`.
- Non-MOCHI examples map `words -> word_senses -> word_examples -> word_example_localizations`.
- Missing non-MOCHI sense/example translations can call Azure Translator, save localizations, and return translated response.

### User Vocabulary Endpoints

| Method | Path | Auth | Query/body | Response result | Common errors | Frontend notes |
|---|---|---|---|---|---|---|
| POST | `/user-vocabularies` | Yes | `UserVocabularyRequest` | `UserVocabularyResponse` | `USER_NOT_FOUND`, `WORD_NOT_FOUND`, `INVALID_USER_VOCABULARY_REQUEST`, `USER_VOCABULARY_ALREADY_EXISTS` | Exactly one of `senseId` or `senseLocalizedId` must be sent; duplicate checks use `userId + wordId + senseId` or `userId + wordId + senseLocalizedId` |
| POST | `/user-vocabularies/review-attempts` | Yes | `SubmitReviewAttemptRequest` | `UserVocabAttemptResponse` | `USER_NOT_FOUND`, `USER_VOCABULARY_NOT_FOUND`, `LISTEN_AND_TYPE_CHALLENGE_NOT_FOUND`, `INVALID_EXERCISE_TYPE` | Also updates review schedule for `VOCAB_*` attempts |
| POST | `/user-vocabularies/search-history` | Yes | `UserSearchHistoryRequest` | `UserSearchHistoryResponse` | `USER_NOT_FOUND`, `WORD_NOT_FOUND` | Separate insert endpoint; `/word-data/word` also records history if `userId` is present |
| GET | `/user-vocabularies/search-history` | Yes | `userId`, `page=0`, `limit=20` | `Page<UserSearchHistoryResponse>` | `USER_NOT_FOUND` | Sorted by newest search |
| GET | `/user-vocabularies/attempts` | Yes | `userId`, `from`, `to`, `page=0`, `limit=20`, `type?` | `Page<UserVocabAttemptResponse>` | `USER_NOT_FOUND` | `from`/`to` are `YYYY-MM-DD`; type can be `VOCAB`, `QUIZ`, `LAT`, or prefix |
| GET | `/user-vocabularies/by-level` | Yes | `userId`, `level`, `page=0`, `limit=20` | `Page<UserVocabularyResponse>` | `USER_NOT_FOUND` | Level should be 1-6; response includes `word` from `words.word` |
| GET | `/user-vocabularies/statistics/daily` | Yes | `userId` | `UserVocabularyStatisticResponse` | `USER_NOT_FOUND` | Derived from attempts for current backend date; omits `wrongCountVocab`; `correctUniqueVocab` excludes vocab that had any wrong attempt that day |
| GET | `/user-vocabularies/statistics/overall` | Yes | `userId` | `UserVocabularyStatisticResponse` | `USER_NOT_FOUND` | `mostWrongVocabIds` uses threshold `> 5` wrong attempts; omits `correctUniqueVocab` and `wrongUniqueVocab`; keeps `wrongCountVocab` |
| GET | `/user-vocabularies/{userVocabId}/word` | Yes | Path `userVocabId` | `WordResponse` | `USER_VOCABULARY_NOT_FOUND`, `WORD_NOT_FOUND` | Returns word detail filtered down to saved sense/localized sense |

Save vocab request:

```json
{
  "userId": "uuid",
  "wordId": "uuid",
  "senseId": "uuid",
  "senseLocalizedId": null,
  "level": 1
}
```

Listen-and-type attempt request:

```json
{
  "attemptId": "challenge-id",
  "userId": "uuid",
  "userVocabId": null,
  "exerciseType": "LAT_LISTEN_AND_TYPE",
  "userAnswer": "typed answer",
  "correct": true,
  "replayCount": 1
}
```

Review schedule notes:

- New saved vocab starts at `level=1`, `currentLevelCorrectTurns=0`, `nextReviewAt=now + 2h`.
- Correct vocab attempts can advance level after enough turns.
- Wrong vocab attempts reset current level turns and adjust next review time.
- Backend trusts `correct` from the request in current code.

### Exercise Endpoints

| Method | Path | Auth | Query/body | Response result | Common errors | Frontend notes |
|---|---|---|---|---|---|---|
| POST | `/exercises/user-lessons` | Yes | `UserLessonRequest` | `UserLessonResponse` | `USER_NOT_FOUND`, `LESSON_NOT_FOUND`, `INVALID_LESSON_TYPE` | Creates a user lesson record |
| GET | `/exercises/user-lessons/progress` | Yes | `userId`, `lessonId`, `lessonType` | `UserLessonProgressResponse` | `USER_NOT_FOUND`, `LESSON_NOT_FOUND`, `INVALID_LESSON_TYPE` | For listen-and-type, checks completed challenge attempts |
| GET | `/exercises/listen-and-type/lesson` | Yes | `userId`, `lessonId` | `ListenAndTypeLessonResponse` | `USER_NOT_FOUND`, `LESSON_NOT_FOUND` | Challenges sorted by `position`; each challenge has `isDone`; backend returns `solution`, do not show it |
| GET | `/exercises/listen-and-type/categories` | No | None | `ListeningCategoryResponse[]` | Auth errors | Use for category list |
| GET | `/exercises/listen-and-type/sub-categories` | No | `categoryId` | `string[]` | `CATEGORY_NOT_FOUND` | Returns display names sorted naturally; names like `conversation_section_3`, `short_stories_section_15`, `toefl_section_2`, `toefl_listening_section_2` are returned as `Section 3`, `Section 15`, `Section 2` |
| GET | `/exercises/listen-and-type/lessons` | No | `subCategoryName`, `userId` | `ListenExerciseSummaryResponse[]` | `USER_NOT_FOUND` | Accepts raw or display sub-category name; lessons are sorted naturally, e.g. `Cam 1`, `Cam 2`, `Cam 10`; response includes `totalPart` challenge count and `completedPart` user completion count |
| GET | `/exercises/vocab-review` | Yes | `userId`, `totalReviewVocab`, `langCode=vi` | `VocabReviewQuizResponse[]` | `USER_NOT_FOUND`, `INVALID_REVIEW_VOCAB_TOTAL`, `WORD_EXAMPLE_NOT_FOUND` | `totalReviewVocab` must be 30, 60, or 90; exhausted words are skipped, so result can contain fewer quizzes |
| GET | `/exercises/vocab-review/word` | Yes | `userId`, `userVocabId`, `langCode=vi` | `VocabReviewQuizResponse[]` | `USER_NOT_FOUND`, `USER_VOCABULARY_NOT_FOUND`, `WORD_EXAMPLE_NOT_FOUND` | Use after a wrong answer to request another quiz for one saved vocab; returns empty list when no valid quiz type remains |

`GET /exercises/vocab-review` sets Redis markers:

| Key pattern | TTL |
|---|---|
| `current_review:<wordId>:<userId>:<exerciseType>` | 2 hours |

These markers prevent repeating the same exercise type for the same word in the current review window.

Review quiz exhaustion behavior:

- If all available vocab quiz types were already generated for the word in the current review window, `/exercises/vocab-review/word` returns an empty list.
- If 4 types were already generated and the only remaining type is a sound-based vocab quiz, but the word/session has no usable sound options, the backend marks that sound-based type as reviewed and returns an empty list.
- In the normal batch endpoint `/exercises/vocab-review`, words that cannot produce a remaining valid quiz are skipped.
- If a sound-based vocab quiz is selected and no usable sound choices exist, the backend marks that type in Redis and tries another available type for the same word.
- Before quiz assembly, standard vocab senses are checked in one batch. Each `(wordId,senseId)` is brought to at least four distinct examples; only the deficit is requested from Groq. Generated English examples are stored with Vietnamese localizations (`langCode=vi`, `reviewStatus=1`). MOCHI/localized senses are not sent to Groq.
- Groq generation is best-effort and does not fail the review endpoint. If `VOCAB_CHOOSE_WORD_IN_SENTENCE_BLANK`, `VOCAB_FILL_WORD_IN_SENTENCE_BLANK`, `VOCAB_SENTENCE_TO_MEANING`, or `VOCAB_SENTENCE_BLANK_TO_SOUND` still has no matching example, that type is marked reviewed and the backend tries another type.
- Each returned review quiz includes `word`, `pos`, saved `sense`/`wordSense`, one preferred `sound`, and one `example` when available. Examples are matched to the saved vocabulary sense: MOCHI/localized vocab uses `senseLocalizedId` against `wordSenseLocalizationId`; normal vocab uses `senseId`.
- `VOCAB_FILL_WORD_IN_SENTENCE_BLANK` now replaces the target word inside `sentence` with a hinted `maskedWord`; `metadata` maps hidden character indexes to their correct characters, same style as `VOCAB_FILL_MISSING_WORD_PART`.
- For `VOCAB_MEANING_TO_SOUND`, `VOCAB_SENTENCE_TO_MEANING`, and `VOCAB_SENTENCE_BLANK_TO_SOUND`, `correctAnswer` is the string form of the correct key inside `metadata`; metadata keys are `1..4`.
- Review quiz cache is keyed by the saved vocab sense, not just word: `review_quiz:v2:sense:<senseId>:<exerciseType>` or `review_quiz:v2:sense_localized:<senseLocalizedId>:<exerciseType>`.

### Learning Resource Endpoints

| Method | Path | Auth | Request | Response result | Common errors | Frontend notes |
|---|---|---|---|---|---|---|
| GET | `/learning-resources/ielts-reading-sources` | No | `page=0`, `limit=20` | `Page<IeltsReadingSourceResponse>` | Auth errors | Public paginated list of IELTS reading resources |
| GET | `/learning-resources/ielts-reading-sources/categories` | No | None | `string[]` | Auth errors | Distinct `name` values from IELTS reading resources |
| GET | `/learning-resources/ielts-reading-sources/by-category` | No | `name`, `page=0`, `limit=20` | `Page<IeltsReadingSourceResponse>` | Auth errors | Public paginated list filtered by exact `name` |
| GET | `/learning-resources/ielts-reading-sources/{readingId}/quiz` | Yes | Path `readingId`, query `userId` | `IeltsReadingQuizResponse` | `IELTS_READING_SOURCE_NOT_FOUND`, auth errors | Reads Redis key `reading_quiz:<readingId>`; on miss joins quiz groups and questions, caches quiz without user-specific completed ids, then adds `completed_question_ids` from `user_vocab_attempts.attempt_id` |
| GET | `/learning-resources/ielts-writing/topics` | Yes | `taskType` | `string[]` | Auth errors | Distinct `problem_topic` values for the task type |
| GET | `/learning-resources/ielts-writing/problems` | Yes | `topic_name` or `topicName` | `IeltsWritingProblemSummaryResponse[]` | Auth errors | Problems for one exact `problem_topic`; each item has `id` and `problem` |
| GET | `/learning-resources/ielts-writing/problems/{problemId}` | Yes | Path `problemId` | `IeltsWritingExercise` | `IELTS_WRITING_EXERCISE_NOT_FOUND`, auth errors | Returns the writing exercise entity, including `evaluationPrompt` |
| GET | `/learning-resources/ielts-writing/problems/{problemId}/bands` | Yes | Path `problemId` | `string[]` | Auth errors | Distinct non-empty `band` values for references of the problem |
| GET | `/learning-resources/ielts-writing/problems/{problemId}/references` | Yes | Path `problemId`, query `band` | `IeltsWritingReference[]` | Auth errors | References filtered by `ielts_writing_exercise_id` and exact `band` |
| POST | `/learning-resources/ielts-writing/reviews` | Yes | `IeltsWritingReviewRequest` | `string` JSON | `IELTS_WRITING_EXERCISE_NOT_FOUND`, `INVALID_WRITING_REVIEW_REQUEST`, `WRITING_REVIEW_FAILED`, auth errors | Finds `ielts_writing_exercise` by `exerciseId`, combines strict reviewer prompt with `evaluationPrompt` and `userAnswer`, calls Groq `openai/gpt-oss-120b`, returns model JSON string |
| POST | `/learning-resources/ielts-reading-sources` | Yes | `InsertIeltsReadingSourceRequest` | `IeltsReadingSourceResponse` | `CATEGORY_NOT_FOUND`, validation | Admin/import-like API |
| POST | `/learning-resources/listen-exercises` | Yes | None | `string` | Auth errors | Placeholder only |
| POST | `/learning-resources/quizzes/listen-and-type` | Yes | None | `string` | Auth errors | Placeholder only |
| POST | `/learning-resources/quizzes/listen-and-answer` | Yes | None | `string` | Auth errors | Placeholder only |
| POST | `/learning-resources/quizzes/reading` | Yes | None | `string` | Auth errors | Placeholder only |
| POST | `/learning-resources/quizzes/generate-reading-listening` | Yes | None | `string` | Auth errors | Placeholder only |

```json
{
  "name": "Cambridge IELTS",
  "title": "Reading passage title",
  "categorySlug": "ielts-reading",
  "content": "Long reading content"
}
```

### Notification Endpoints

| Method | Path | Auth | Request | Response result | Common errors | Frontend notes |
|---|---|---|---|---|---|---|
| POST | `/notifications/send` | Yes | `SendNotificationRequest` | `string` | `NOTIFICATION_TEMPLATE_NOT_FOUND`, `UNSUPPORTED_NOTIFICATION_METHOD`, `PUSH_TOKEN_NOT_FOUND`, `NOTIFICATION_SEND_FAILED` | `EMAIL` uses `recipientId` directly as email |

```json
{
  "recipientId": "user@example.com",
  "title": "Title",
  "notificationMethod": "EMAIL",
  "notificationType": "PRE_REGISTER",
  "metadata": {
    "username": "bach",
    "code": "123456"
  }
}
```

### Word Info Insert Endpoints

These are authenticated insert/admin-like APIs. They return `ApiResponse<string>` with message `Inserted successfully`.

| Method | Path | Request body |
|---|---|---|
| POST | `/word-info/categories` | `InsertWordCategoriesRequest` |
| POST | `/word-info/senses` | `InsertWordSenseRequest` |
| POST | `/word-info/sense-localizations` | `InsertWordSenseLocalizationRequest` |
| POST | `/word-info/relations` | `InsertWordRelationRequest` |
| POST | `/word-info/examples` | `InsertWordExampleRequest` |
| POST | `/word-info/example-localizations` | `InsertWordExampleLocalizationRequest` |
| POST | `/word-info/idioms` | `InsertWordIdiomRequest` |
| POST | `/word-info/idiom-translations` | `InsertWordIdiomTranslationRequest` |
| POST | `/word-info/sounds` | `InsertWordSoundRequest` |

## 8. User Flows

### Register, Login, Session Restore

1. Register: `POST /auth/register` with `username`, `password`, `email`.
2. Verification email is sent; backend stores Redis `pre_register_info:<email>` and `preRegister:<email>` for 5 minutes.
3. Verify: `POST /auth/verify-email` with `email`, `code`.
4. Login: `POST /auth/login`.
5. Store `token`, `userId`, `username`.
6. Session restore: read local token/userId and call `GET /users/info?userId=...` with Bearer token.
7. Logout: call `POST /auth/logout` with token, then clear local session.

### Search Word

1. Normalize frontend input to lowercase trim if desired.
2. Call `GET /word-data/words/basic-search?text=<normalized>&isAutocomplete=true` for lightweight autocomplete `Word[]`, `GET /word-data/words/basic-search?text=<normalized>` for exact lightweight search, or `GET /word-data/words/search?text=<normalized>&isTrans=true&transLangCode=vi` for full detail list.
3. Backend searches only `normalizedWord`; `basic-search` can do prefix search when `isAutocomplete=true`.
4. Show matching word objects; call word detail for the selected `wordId`.

### Word Detail

1. Call `GET /word-data/word?wordId=...&isTrans=true&transLangCode=vi&userId=...`.
2. Use `senses[]`; examples are inside each `sense.examples[]`.
3. Use `sounds[]` for pronunciation/audio.
4. Use `forms[]`, `relation`, and `idioms[]` for extra tabs/sections.
5. For MOCHI words, save localized sense by `sense.localizationId`.

### Save Vocabulary

1. From a `WordSenseResponse`, choose:
   - Non-MOCHI or normal sense: send `senseId`.
   - MOCHI localized sense: send `senseLocalizedId` from `localizationId`.
2. Call `POST /user-vocabularies`.
3. Store/use returned `UserVocabularyResponse`.
4. If the same user already saved the same `wordId + senseId`, backend returns code `2023` with message `wordId + senseId already exists`.
5. If the same user already saved the same `wordId + senseLocalizedId`, backend returns code `2023` with message `wordId + senseLocalizedId already exists`.

### Review Session

1. Call `GET /exercises/vocab-review?userId=...&totalReviewVocab=30&langCode=vi`.
2. Render each `VocabReviewQuizResponse` according to `exerciseType`.
3. After each answer, call `POST /user-vocabularies/review-attempts`.
4. If the answer is wrong and the frontend wants another quiz for the same saved word, call `GET /exercises/vocab-review/word?userId=...&userVocabId=...&langCode=vi`.
5. Use `correct` result computed by frontend in current backend design.
6. Backend updates review schedule for `VOCAB_*` exercise types.

### Listen-And-Type Exercise

1. Load categories: `GET /exercises/listen-and-type/categories`.
2. Load sub-categories: `GET /exercises/listen-and-type/sub-categories?categoryId=...`; values are display names sorted naturally.
3. Load lessons by sub-category: `GET /exercises/listen-and-type/lessons?subCategoryName=...&userId=...`; backend accepts raw or display sub-category names.
4. Optionally add user lesson: `POST /exercises/user-lessons`.
5. Load lesson: `GET /exercises/listen-and-type/lesson?userId=...&lessonId=...`.
6. Render challenges sorted by `position`.
7. Do not display `solution`.
8. Submit each challenge attempt with `exerciseType=LAT_LISTEN_AND_TYPE` and `attemptId=<challenge.id>`.
9. Reload progress via `GET /exercises/user-lessons/progress`.

### Statistics

Use:

- Daily: `GET /user-vocabularies/statistics/daily?userId=...`
- Overall: `GET /user-vocabularies/statistics/overall?userId=...`

These are derived from `user_vocab_attempts`, not from dedicated statistics tables in current code.

### Streak

Unknown from current backend code. `bussiness_rule.md` describes `user_streak`, but no entity/controller/service/API was found in current backend code.

### Push Token Registration

Unknown from current backend code. `UserPushToken` entity and push send implementation exist, but no API was found for registering/updating a browser/mobile push token.

## 9. TypeScript Interfaces Generated From Current Response DTOs

```ts
export interface ApiResponse<T> {
  code: number;
  message?: string | null;
  traceId?: string | null;
  result?: T | null;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  numberOfElements: number;
  first: boolean;
  last: boolean;
  empty: boolean;
  pageable?: unknown;
  sort?: unknown;
}

export interface AuthenticationResponse {
  token?: string | null;
  userId?: string | null;
  username?: string | null;
}

export interface UserInfoResponse {
  id?: string | null;
  username?: string | null;
  email?: string | null;
  userRole?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface WordResponse {
  wordId?: string | null;
  word?: string | null;
  normalizedWord?: string | null;
  pos?: string | null;
  certLevel?: string | null;
  lang?: string | null;
  langCode?: string | null;
  wordSource?: string | null;
  otherSource?: string | null;
  categories?: string[] | null;
  sounds?: WordSoundResponse[] | null;
  senses?: WordSenseResponse[] | null;
  idioms?: WordIdiomResponse[] | null;
  forms?: WordFormResponse[] | null;
  relation?: WordRelationResponse | null;
}

export interface WordSenseResponse {
  senseId?: string | null;
  localizationId?: string | null;
  wordId?: string | null;
  word?: string | null;
  pos?: string | null;
  certLevel?: string | null;
  shortMeaning?: string | null;
  definition?: string | null;
  synonyms?: string[] | null;
  antonyms?: string[] | null;
  examples?: WordExampleResponse[] | null;
  trans?: WordSenseTranslation | null;
  derived?: string[] | null;
  coordinateTerms?: string[] | null;
  formOf?: string[] | null;
  altOf?: string[] | null;
}

export interface WordSenseTranslation {
  langCode?: string | null;
  shortMeaning?: string | null;
  definition?: string | null;
}

export interface WordExampleResponse {
  wordExampleId?: string | null;
  senseId?: string | null;
  wordSenseLocalizationId?: string | null;
  wordId?: string | null;
  word?: string | null;
  pos?: string | null;
  certLevel?: string | null;
  sentence?: string | null;
  trans?: string | null;
}

export interface WordSoundResponse {
  wordId?: string | null;
  ipa?: string | null;
  tags?: string[] | null;
  soundSource?: string | null;
  oggUrl?: string | null;
  mp3Url?: string | null;
  enpr?: string | null;
}

export interface WordFormResponse {
  wordId?: string | null;
  word?: string | null;
  pos?: string | null;
  certLevel?: string | null;
  form?: string | null;
  tags?: string[] | null;
}

export interface WordRelationResponse {
  wordId?: string | null;
  word?: string | null;
  pos?: string | null;
  certLevel?: string | null;
  synonyms?: string[] | null;
  antonyms?: string[] | null;
  derived?: string[] | null;
  coordinateTerms?: string[] | null;
  formOf?: string[] | null;
  altOf?: string[] | null;
}

export interface WordIdiomResponse {
  wordId?: string | null;
  word?: string | null;
  pos?: string | null;
  certLevel?: string | null;
  idiom?: string | null;
  definition?: string | null;
  example?: string | null;
  example2?: string | null;
  trans?: WordIdiomTranslation | null;
}

export interface WordIdiomTranslation {
  idiom?: string | null;
  definition?: string | null;
  example?: string | null;
  example2?: string | null;
}

export interface Word {
  id?: string | null;
  word?: string | null;
  normalizedWord?: string | null;
  pos?: string | null;
  lang?: string | null;
  langCode?: string | null;
  wordSource?: string | null;
  otherSource?: string | null;
  certLevel?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface Category {
  id?: string | null;
  name?: string | null;
  slug?: string | null;
  description?: string | null;
  createdAt?: string | null;
}

export type ExerciseType =
  | "VOCAB_WORD_TO_MEANING"
  | "VOCAB_FILL_MISSING_WORD_PART"
  | "VOCAB_LISTEN_AND_TYPE_WORD"
  | "VOCAB_CHOOSE_WORD_IN_SENTENCE_BLANK"
  | "VOCAB_FILL_WORD_IN_SENTENCE_BLANK"
  | "VOCAB_MEANING_TO_SOUND"
  | "VOCAB_SENTENCE_TO_MEANING"
  | "VOCAB_SENTENCE_BLANK_TO_SOUND"
  | "LAT_LISTEN_AND_TYPE";

export interface UserVocabularyResponse {
  id?: string | null;
  userId?: string | null;
  wordId?: string | null;
  word?: string | null;
  senseId?: string | null;
  senseLocalizedId?: string | null;
  level?: number | null;
  currentLevelCorrectTurns?: number | null;
  nextReviewAt?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface UserVocabAttemptResponse {
  id?: string | null;
  attemptId?: string | null;
  userId?: string | null;
  userVocabId?: string | null;
  exerciseType?: ExerciseType | null;
  userAnswer?: string | null;
  correct?: boolean | null;
  replayCount?: number | null;
  createdAt?: string | null;
}

export interface UserSearchHistoryResponse {
  id?: string | null;
  userId?: string | null;
  wordId?: string | null;
  word?: string | null;
  searchedAt?: string | null;
}

export interface WrongVocabResponse {
  userVocabId?: string | null;
  word?: string | null;
  wrongCount?: number | null;
}

export interface UserVocabularyStatisticResponse {
  userId?: string | null;
  statisticDate?: string | null;
  totalAttempts?: number | null;
  correctQuizAttempt?: number | null;
  wrongQuizAttempt?: number | null;
  totalUniqueVocab?: number | null;
  // Present for daily statistics; omitted for overall statistics.
  correctUniqueVocab?: number | null;
  // Present for daily statistics; omitted for overall statistics.
  wrongUniqueVocab?: number | null;
  // Present for overall statistics; omitted for daily statistics.
  wrongCountVocab?: number | null;
  wrongVocabIds?: WrongVocabResponse[] | null;
  mostWrongVocabIds?: WrongVocabResponse[] | null;
}

export interface UserLessonResponse {
  id?: string | null;
  userId?: string | null;
  lessonId?: string | null;
  lessonType?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface UserLessonProgressResponse {
  userId?: string | null;
  lessonId?: string | null;
  lessonType?: string | null;
  completedChallengeIds?: string[] | null;
}

export interface ListenAndTypeLessonResponse {
  userId?: string | null;
  lessonId?: string | null;
  title?: string | null;
  categoryName?: string | null;
  fullDocument?: string | null;
  speechToTextLangCode?: string | null;
  audioUrl?: string | null;
  learningResourceType?: string | null;
  completedChallengeIds?: string[] | null;
  challenges?: ListenAndTypeChallengeResponse[] | null;
}

export interface ListenAndTypeChallengeResponse {
  id?: string | null;
  position?: number | null;
  content?: string | null;
  jsonContent?: string | null;
  solution?: string | null;
  timeStart?: number | null;
  timeEnd?: number | null;
  hints?: string | null;
  audioSrc?: string | null;
  isDone?: boolean | null;
}

export interface ListeningCategoryResponse {
  id?: string | null;
  categoryName?: string | null;
  slug?: string | null;
  description?: string | null;
}

export interface ListenExerciseSummaryResponse {
  id?: string | null;
  title?: string | null;
  speechToTextLangCode?: string | null;
  totalPart?: number | null;
  completedPart?: number | null;
}

export interface VocabReviewQuizResponse {
  wordId?: string | null;
  userVocabId?: string | null;
  word?: string | null;
  pos?: string | null;
  sound?: WordSoundResponse | null;
  example?: WordExampleResponse | null;
  sense?: WordSenseResponse | null;
  wordSense?: WordSenseResponse | null;
  exerciseType?: ExerciseType | null;
  correctAnswer?: string | null;
  listAnswers?: string[] | null;
  metadata?: Record<number, string> | null;
  maskedWord?: string | null;
  audioUrl?: string | null;
  missIndex?: number | null;
  sentence?: string | null;
  trans?: string | null;
}

export interface IeltsReadingSourceResponse {
  id?: string | null;
  name?: string | null;
  title?: string | null;
  categoryId?: string | null;
  content?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}
export interface IeltsReadingQuizResponse {
  quiz?: IeltsReadingQuiz | null;
  id?: string | null;
  completed_question_ids?: string[] | null;
}

export interface IeltsReadingQuiz {
  title?: string | null;
  module?: string | null;
  passage_analysis?: IeltsReadingPassageAnalysis | null;
  question_groups?: IeltsReadingQuestionGroup[] | null;
  id?: string | null;
}

export interface IeltsReadingPassageAnalysis {
  paragraph_count?: number | null;
  text_type?: string | null;
  writer_view_present?: boolean | null;
  process_present?: boolean | null;
  multi_entity_present?: boolean | null;
  selected_question_types?: IeltsReadingQuestionType[] | null;
}

export type IeltsReadingQuestionType =
  | 'matching_features'
  | 'matching_headings'
  | 'matching_information'
  | 'multiple_choice_multiple'
  | 'multiple_choice_single'
  | 'sentence_completion'
  | 'short_answer'
  | 'summary_completion'
  | 'true_false_not_given'
  | 'yes_no_not_given';

export interface IeltsReadingQuestionGroup {
  group_id?: string | null;
  question_type?: IeltsReadingQuestionType | string | null;
  instruction?: string | null;
  question_number_start?: number | null;
  question_number_end?: number | null;
  context?: string | null;
  allow_option_reuse?: boolean | null;
  word_limit?: string | null;
  source_paragraph_ids?: string[] | null;
  shared_options?: string[] | null;
  questions?: IeltsReadingQuestion[] | null;
}

export interface IeltsReadingQuestion {
  question_id?: string | null;
  number?: number | null;
  stem?: string | null;
  options?: string[] | null;
  answer?: string[] | null;
  source_paragraph_id?: string | null;
  evidence_quote?: string | null;
  explanation?: string | null;
  difficulty?: string | null;
  skill?: string | null;
}

export interface IeltsWritingProblemSummaryResponse {
  id?: string | null;
  problem?: string | null;
}

export interface IeltsWritingExercise {
  id?: string | null;
  problem?: string | null;
  problemTopic?: string | null;
  taskType?: number | null;
  evaluationPrompt?: string | null;
  imageUrl?: string | null;
  imageDescription?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface IeltsWritingReference {
  id?: string | null;
  ieltsWritingExerciseId?: string | null;
  essay?: string | null;
  band?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}
```

Request interfaces:

```ts
export interface RegisterUserRequest {
  username: string;
  password: string;
  email: string;
  userRole?: string | null;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface VerifyEmailRequest {
  email: string;
  code: string;
}

export interface LogoutRequest {
  token: string;
}

export interface RefreshTokenRequest {
  token: string;
}

export interface ResetPasswordRequest {
  userId: string;
  oldPassword: string;
  newPassword: string;
}

export interface ForgetPasswordRequest {
  email: string;
}

export interface UpdateUserInfoRequest {
  userId: string;
  username: string;
}

export interface UserVocabularyRequest {
  userId: string;
  wordId: string;
  senseId?: string | null;
  senseLocalizedId?: string | null;
  level?: number | null;
}

export interface SubmitReviewAttemptRequest {
  attemptId?: string | null;
  userId: string;
  userVocabId?: string | null;
  exerciseType: ExerciseType;
  userAnswer?: string | null;
  correct: boolean;
  replayCount?: number | null;
}

export interface UserSearchHistoryRequest {
  userId: string;
  wordId: string;
}

export interface IeltsWritingReviewRequest {
  exerciseId: string;
  userId: string;
  userAnswer: string;
}
export interface UserLessonRequest {
  userId: string;
  lessonId: string;
  lessonType: string;
}
```

## 10. Recommended Frontend Pages And Components

Pages:

| Page | Purpose |
|---|---|
| Login/Register/Forgot Password | Auth flows |
| Session Restore Gate | Validate stored token via `/users/info` |
| Word Search | Basic search by `normalizedWord` and select word |
| Word Detail | Senses, examples per sense, audio, forms, relations, idioms |
| Saved Vocabulary | List by level and open saved word detail |
| Review Session | Render `VocabReviewQuizResponse` by `exerciseType` |
| Review Results/History | Attempts page and statistics summary |
| Listen Categories | Category list |
| Listen Lesson List | Lessons by category |
| Listen-And-Type Player | Audio/transcript/challenge flow |
| Profile Settings | Show user info and update username |

Core components:

| Component | Backend data |
|---|---|
| `ApiErrorBanner` | `ApiResponse.code`, `message`, `traceId` |
| `AuthProvider` | token/userId/username, Bearer header |
| `WordSearchBox` | `/word-data/words/basic-search` |
| `WordSenseList` | `WordResponse.senses[]` |
| `SenseExampleList` | `WordSenseResponse.examples[]` |
| `SaveVocabularyButton` | `UserVocabularyRequest` |
| `AudioButton` | `WordSoundResponse.mp3Url` or `oggUrl` |
| `ReviewQuizRenderer` | `ExerciseType` switch |
| `ListenChallengeRenderer` | `ListenAndTypeChallengeResponse`, hiding `solution` |
| `PaginationControls` | Spring `Page<T>` |

## 11. Frontend Error Handling Guide

Recommended handling:

| Code/status | Frontend action |
|---|---|
| `1001` / 401 | Clear local auth and redirect to login |
| `8888` | Show field validation message from `message` |
| `2001`, `2002` | Show duplicate username/email message on register/profile form |
| `2005`, `2006` | Ask user to retry verification flow |
| `2007` | Show invalid password |
| `2008` | Refresh token failed; force login |
| `2009` | Word not found empty state |
| `2018` | Review size must be 30, 60, or 90 |
| `2019` | Current review exhausted; tell user to wait or choose another word set |
| `2020`, `2011` | Quiz cannot be generated due missing data |
| `2024` | IELTS reading source not found |
| `2025` | IELTS writing exercise not found |
| `2026` | Invalid writing review request |
| `2027` | Writing review failed |
| `2028` | Groq API key is not configured |
| `3001`-`3004` | Notification send failed; do not block core learning flow unless required |

Always show `traceId` in developer/debug surfaces so backend logs can be matched.

## 12. Open Questions And Missing Backend Pieces

| Topic | Status |
|---|---|
| Streak API | Unknown from current backend code; no controller/service/entity found |
| Push token registration API | Unknown from current backend code; `UserPushToken` entity exists, but no register/update endpoint |
| Cookie/session restore | Unknown from current backend code; auth is token-only |
| Current authenticated user endpoint | No `/me`; frontend must call `/users/info?userId=...` |
| Refresh token user fields | Current refresh returns only `token`; `userId`/`username` are null |
| Role-based frontend permissions | No explicit role endpoint; user role only appears in `UserInfoResponse` |
| Search behavior | Exact match on `normalizedWord`; no fuzzy/prefix search in current repository |
| Translation cache key | Word cache keys do not include `transLangCode`, so multilingual cache correctness is unclear |
| Listen challenge solution exposure | Backend returns `solution` in lesson detail; frontend should not show it to learners |
| Backend-calculated correctness | Review submit currently trusts request `correct`; stronger server-side grading is not implemented |
| Dedicated statistics tables | Business rule mentions statistics tables, but current statistics API derives from attempts |

## 13. Final Ready-To-Copy Frontend-Agent Prompt

```text
You are building the frontend for a Spring Boot vocabulary learning backend.

Use the backend context in docs/frontend_context.md as the source of truth. Do not invent APIs or fields. Base URL is http://localhost:8080/vocab-learning. All non-auth endpoints require Authorization: Bearer <token>. Responses are wrapped in ApiResponse<T> with code, message, traceId, result.

Build a frontend that supports:
- Register -> verify email -> login -> token-based session restore.
- User profile info and username update.
- Word search using /word-data/words/basic-search or /word-data/words/search. Search text maps to exact normalizedWord.
- Word detail using /word-data/word with senses, examples inside each sense, sounds, forms, relation, idioms.
- Save vocabulary by sending exactly one of senseId or senseLocalizedId.
- Review sessions using /exercises/vocab-review and submit attempts with /user-vocabularies/review-attempts.
- IELTS reading resource list and quiz rendering using /learning-resources/ielts-reading-sources and /learning-resources/ielts-reading-sources/{readingId}/quiz.
- IELTS writing browse flow using /learning-resources/ielts-writing/topics, /problems, /problems/{problemId}/bands, and /references.
- IELTS writing review using POST /learning-resources/ielts-writing/reviews with exerciseId, userId, and userAnswer; result is a JSON string from Groq.
- Listen-and-type flow: categories, lessons, lesson detail, progress, submit LAT_LISTEN_AND_TYPE attempts.
- Search history, attempts, daily statistics, overall statistics.

Do not expose sensitive values. Store JWT carefully. Do not render listen challenge solution even though backend currently returns it. Treat jsonContent and hints as raw JSON strings and parse defensively. Show backend traceId in error details. Mark missing features as unavailable: streak and push token registration are not implemented in current backend code.

Use the TypeScript interfaces from docs/frontend_context.md and keep exact JSON field names.
```
