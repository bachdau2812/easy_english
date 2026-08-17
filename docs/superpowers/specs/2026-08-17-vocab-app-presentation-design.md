# Vocab App Graduation Presentation Design

## Objective

Create a complete Vietnamese PowerPoint deck for a graduation-project committee. The presentation must explain the learning problem, the data foundation, the system architecture, the main processing flows, the most important implemented features, deployment, and achieved results.

The deck is a design specification only at this stage. PowerPoint authoring begins only after a later explicit request.

## Communication Job

By the end of the presentation, the graduation-project committee should understand that Vocab App is not merely a CRUD application: it transforms a large shared English-learning dataset into a closed learning loop while keeping each user's saved meanings, attempts, review level, and schedule independent.

## Audience and Delivery Constraints

- Audience: graduation-project/thesis committee.
- Duration: approximately 15 minutes.
- Language: Vietnamese.
- Format: 16:9 PowerPoint.
- Target length: 15 slides.
- Style: academic and technical.
- Visual system: white background, strong black hierarchy, restrained blue accents, thin rules, and clear spacing.
- Visual evidence: architecture diagrams, data pipelines, sequence flows, schedules, and data figures.
- Do not use application screenshots.
- Avoid dense code listings and long paragraphs.
- Slide titles should state the slide's takeaway rather than merely name a topic.

## Narrative Approach

Use the lifecycle of data and learning as the narrative:

1. Establish the learner's problem.
2. Show how Vocab App closes the learning loop.
3. Explain how source data is collected, normalized, and structured.
4. Present the architecture and separation between shared and personal data.
5. Explain the feature flows, with extra depth on Review Vocab.
6. Show how the application is deployed.
7. Close with achieved results and concise future improvements.

## Storyboard

### Slide 1 — Vocab App: dữ liệu dùng chung, lộ trình học cá nhân

- Minimal title slide.
- Include project name and primary stack: React, Spring Boot, MySQL, Redis, and AI.
- Include student information:
  - Đậu Đức Bách.
  - Student ID 22000071.
  - K67A2 — Toán Tin.
- Use one blue rule or geometric accent; do not add an agenda to the cover.

### Slide 2 — Người học dùng nhiều công cụ nhưng vẫn khó ôn đúng nội dung

Present three connected problems:

- Learning data is fragmented across dictionary, note-taking, listening, and IELTS tools.
- A word can have multiple meanings; saving only the word string risks learning the wrong sense.
- Without a schedule based on attempts, users do not know what should be reviewed next.

Conclude that the problem is not only content availability but the lack of a connected learning process.

### Slide 3 — Ứng dụng khép kín vòng tra cứu – ghi nhớ – luyện tập – đánh giá

Show one four-stage learning loop:

1. Search a word and understand the correct sense.
2. Save the selected sense to a personal vocabulary collection.
3. Review with multiple exercise types and an adaptive schedule.
4. Apply English through Listen-and-Type and IELTS Writing with AI feedback.

Review Vocab should appear as the central bridge between shared content and personal progress.

### Slide 4 — Dữ liệu được chuẩn hóa trước khi trở thành nội dung học

Show a left-to-right pipeline:

```text
KAIKKI + Vietnamese supplemental API + public Listening/Reading data
→ collection with rate limiting, retry, and resume
→ JSON validation and field cleaning
→ word/POS normalization
→ duplicate detection and deterministic merge
→ relational TSVs and MySQL entities
```

Explain that KAIKKI is the canonical vocabulary axis. Vietnamese data supplements localization, examples, pronunciation, audio, and idioms instead of replacing the source record.

### Slide 5 — Kho dữ liệu tạo nền tảng cho nhiều tình huống học tập

Use large, clearly labelled figures. Distinguish unique word strings from database rows:

- 1,334,872 distinct word strings: `COUNT(DISTINCT word)`.
- 1,473,332 rows in `words`: `COUNT(*)`.
- 1,762,690 rows in `word_senses`.
- 1,017,860 rows in `word_examples`.
- 1,383 listening lessons.
- 47,304 listening challenges.
- 226 Reading passages.
- 2,916 Reading questions.

Never label 1,473,332 as the number of distinct words.

### Slide 6 — Kiến trúc phân lớp giúp tách giao diện, nghiệp vụ và lưu trữ

Use one four-layer architecture diagram:

```text
React web application
→ Spring Boot REST API and security
→ dictionary, review, listening, and IELTS services
→ MySQL, Redis, Azure Translator, and Groq AI
```

Show that controllers coordinate requests, services own business rules, repositories access MySQL, and Redis supports caches and short-lived states.

### Slide 7 — Một nội dung dùng chung có thể phục vụ nhiều tiến độ học riêng

Use three visual groups:

- Shared content: words, senses, localizations, examples, sounds, lessons, and IELTS resources.
- Personal state: UserVocabulary, saved sense, level, correct turns, nextReviewAt, search history, and attempts.
- Reconstructable temporary state: word-detail cache, review snapshots, exercise-type reservations, and Reading quiz cache.

This slide must establish the core architecture thesis before the feature flows begin.

### Slide 8 — Cache-aside rút ngắn luồng tra cứu chi tiết từ

Show the lookup flow:

```text
Basic search/autocomplete → MySQL normalized-word query
Selected wordId → word-detail cache lookup
Cache hit → deserialize WordResponse
Cache miss → assemble word, senses, examples, sounds, forms, idioms, relations, and categories
Missing standard translation → Azure Translator → persist localization
Cache full WordResponse for five hours → return response
Optional userId → insert or refresh search history
```

Display the current cache keys:

- `word_with_trans:<wordId>`.
- `word_without_trans:<wordId>`.

Include a small current-limit callout: the translated key does not include `transLangCode`, so multiple translation languages can collide.

### Slide 9 — Snapshot quiz được tái sử dụng giữa nhiều người dùng

Explain the complete review-generation flow:

1. The user requests 30, 60, or 90 review items.
2. MySQL selects saved vocabularies where `nextReviewAt <= now`, then applies level quotas.
3. Standard senses with fewer than four examples are supplemented through best-effort Groq generation.
4. Redis reads the current word revision and snapshot in batches.
5. Cache misses load words, senses, translations, sounds, and examples from MySQL in bulk.
6. The backend writes reusable snapshots and builds the final quiz in memory.
7. The balanced scheduler selects among eight eligible vocabulary exercise types.

Show the shared snapshot key:

```text
review_vocab_snapshot:v1:<wordId>:<senseKey>:<langCode>:<revision>
```

The key excludes `userId` and `userVocabId`, allowing another user with the same word, sense, and language to reuse the snapshot. Snapshot TTL is six hours plus zero to thirty minutes of jitter.

The final `VocabReviewQuizResponse` is not cached because it contains user-specific IDs, random answer order, level-sensitive masking, and request-scoped distractors.

### Slide 10 — Dữ liệu chung được tái sử dụng nhưng trạng thái retry vẫn thuộc từng người học

Show two related but separate Redis responsibilities.

Exercise-type reservation:

```text
review_progress:v2:<userId>:<wordId>
```

- Redis Sorted Set members are `ExerciseType` values.
- Atomic Lua reservation selects the first unused eligible type.
- Each reservation lasts two hours.
- The whole key has a three-hour cleanup TTL.
- A wrong-answer retry targets the same `userVocabId`, but this user–word reservation causes the next eligible exercise type to be selected.

Wrong-schedule protection:

```text
current_review_wrong:<userVocabId>
```

- TTL is two hours.
- Additional wrong attempts are still stored, but they do not repeatedly penalize the schedule during that window.

State the distinction explicitly: exercise types are tracked by user and word; repeated wrong-schedule updates are guarded by `userVocabId`.

### Slide 11 — Mỗi attempt trực tiếp thay đổi thời điểm ôn tiếp theo

Show a compact level table:

| Level | Correct turns required | Correct before level-up | Correct and level-up | Wrong |
|---|---:|---|---|---|
| 1 | 2 | +2 hours | L2, +6 hours | stay L1, +1 hour |
| 2 | 2 | +5 hours | L3, +12 hours | stay L2, +4 hours |
| 3 | 2 | +9 hours | L4, +1 day | stay L3, +6 hours |
| 4 | 2 | +14 hours | L5, +3 days | stay L4, +12 hours |
| 5 | 4 | +1 day | L6, +14 days | stay L5, +1 day |
| 6 | no further level | correct turns 1/2/3/4+: +14/+30/+60/+90 days | not applicable | L5, +3 days |

Explain that every attempt is persisted first. Vocabulary attempts then update `level`, `currentLevelCorrectTurns`, and `nextReviewAt`. Wrong attempts reset the current correct-turn count.

### Slide 12 — Listen-and-Type biến bài nghe thành các challenge có thể theo dõi

Show this flow:

```text
Category → sub-category → lesson list
→ lesson and ordered challenges
→ play audio or selected time range
→ user types the answer
→ submit LAT_LISTEN_AND_TYPE attempt
→ query distinct completed challenges and completedPart
```

Explain that a challenge carries position, content, solution, audio, start/end time, and hints. Current implementation trusts the frontend-provided `correct` value; completion queries count an existing LAT attempt rather than only correct attempts.

### Slide 13 — AI hoàn thành vòng viết – nhận phản hồi – xem lại

Show the Writing flow:

```text
Select task and topic
→ open problem and optional band reference
→ submit exerciseId, userId, and essay
→ combine strict IELTS examiner instructions, evaluationPrompt, and essay
→ call Groq openai/gpt-oss-120b at temperature 0.1
→ receive one JSON review string
→ store essay and review as IELTS_WRITING_REVIEW attempt
→ retrieve history by user and exercise
```

The AI evaluates Task Response/Achievement, Coherence and Cohesion, Lexical Resource, and Grammatical Range and Accuracy. Explanations are in Vietnamese, while quoted errors and corrections remain in English.

State that Writing review is not Redis-cached; every submission requests a new AI evaluation.

### Slide 14 — Hệ thống được đóng gói và triển khai theo quy trình tự động

Use two compact flows.

Runtime:

```text
User → Cloudflare → Cloudflare Tunnel
→ Nginx and React SPA
→ Spring Boot API
→ MySQL and Redis
```

CI/CD:

```text
Push main → GitHub Actions → Maven/Docker Buildx
→ Docker Hub with latest and commit-SHA tags
→ SSH to Linux server
→ Docker Compose pull and recreate
```

Mention Java 21, multi-stage Docker builds, Nginx reverse proxy, and containerized backend deployment. Do not present internal loopback ports as public endpoints.

### Slide 15 — Hệ thống đã hoàn thiện vòng học cốt lõi và có hướng mở rộng rõ ràng

Summarize achieved outcomes:

- A large normalized relational dataset supports dictionary, listening, and IELTS flows.
- Users save a specific sense rather than an ambiguous word string.
- Review Vocab supports eight exercise types, shared snapshots, personal retry state, level progression, and `nextReviewAt` updates.
- Listen-and-Type and Writing retain attempt history and progress.
- The backend is containerized and supported by a CI/CD workflow.

Keep future improvements to three concise points:

- Complete cache identities, invalidation, and performance optimization.
- Increase backend validation for submitted answers and AI responses.
- Add monitoring and complete the CI/CD workflow.

Close with this message:

> Vocab App tái sử dụng dữ liệu học tập dùng chung, đồng thời duy trì lịch ôn và tiến độ độc lập cho từng người dùng.

Do not end with an isolated generic “Thank you” slide.

## Visual and Layout Rules

- Use the academic technical direction selected during brainstorming.
- Prefer one primary visual claim per slide.
- Use native PowerPoint shapes for simple architecture and process diagrams.
- Keep connectors behind nodes and prevent connectors from crossing labels.
- Use a large-number layout for data results rather than a dense spreadsheet-like table.
- Use the data table layout only for the Level 1–6 schedule.
- Reserve blue for flow direction, Redis/cache emphasis, and selected evidence.
- Use gray for supporting infrastructure and black for durable MySQL/business facts.
- Use red or amber only for a concise current-limit callout.
- Do not use application screenshots, generated decorative images, or unrelated stock photography.
- Add source footers where figures or implementation facts appear, using repository files and verified database-query results.

## Accuracy Constraints

- Treat current source code as authoritative when it conflicts with older frontend documentation.
- Do not describe the review exercise reservation as keyed by `userVocabId`; current code keys it by `userId` and `wordId`.
- Do not describe 1,473,332 database rows as distinct words.
- Use 1,017,860 as the verified `word_examples` row count.
- Do not claim measured latency, cache hit rate, learning effectiveness, AI accuracy, or user growth without measured evidence.
- Present known current limitations as implementation facts, not as failures of the overall architecture.

## Authoring Acceptance Criteria

- Exactly 15 audience-facing slides in Vietnamese.
- The deck can be delivered in approximately 15 minutes.
- Review Vocab receives three dedicated slides.
- The shared-cache versus personal-state distinction is visually explicit.
- Every Redis key and TTL shown matches current source code.
- Database figures match the verified MySQL query results supplied by the project owner.
- No application screenshot is used.
- No visible text is smaller than the presentation skill's minimums.
- No unintended overlaps, clipping, title wrapping, or connector crossings remain after rendering.
- The final PPTX passes slide overflow checks and every rendered slide is inspected individually.
