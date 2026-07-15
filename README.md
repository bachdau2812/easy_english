# Vocab App Backend

Backend cho một ứng dụng học từ vựng tiếng Anh, tập trung vào ba việc chính: tra cứu dữ liệu từ, lưu từ để ôn tập theo lịch, và luyện tập qua quiz/nghe chép chính tả. Project được viết bằng Spring Boot, dùng MySQL để lưu dữ liệu chính và Redis cho cache/session ngắn hạn.

Ứng dụng hiện tại chưa chỉ là một CRUD từ điển đơn giản. Nó đã có khá nhiều nghiệp vụ quanh một từ: nhiều nghĩa, bản dịch nghĩa, ví dụ theo từng nghĩa, âm thanh, idiom, quan hệ từ, phân loại, lịch ôn tập cá nhân và thống kê kết quả học.

## Công Nghệ Chính

| Thành phần | Đang dùng |
|---|---|
| Java | 21 |
| Framework | Spring Boot 4.1.0 |
| Database | MySQL |
| Cache | Redis |
| Auth | JWT Bearer token |
| ORM | Spring Data JPA |
| Validation | Jakarta Validation |
| Mail | Spring Mail |
| Push notification | Firebase Admin SDK |
| Translate | Azure Translator |
| API docs | Swagger/OpenAPI |

Base URL local:

```text
http://localhost:8080/vocab-learning
```

Swagger:

```text
http://localhost:8080/vocab-learning/swagger-ui.html
```

## Các Tính Năng Hiện Có

### 1. Xác Thực Và Tài Khoản

Ứng dụng hỗ trợ đầy đủ các luồng cơ bản cho người dùng:

- Đăng ký tài khoản.
- Gửi và xác thực mã email trước khi tạo user.
- Đăng nhập bằng username/password.
- Trả về JWT token, `userId`, `username` sau khi login.
- Logout bằng cách đưa token vào blacklist trong Redis.
- Refresh token.
- Quên mật khẩu, gửi mã xác thực, đổi mật khẩu.
- Lấy thông tin người dùng.
- Cập nhật thông tin người dùng, hiện tại có thể đổi `username`.

Token được gửi qua header:

```http
Authorization: Bearer <token>
```

Backend không dùng cookie/session login. Session phía frontend nên được khôi phục bằng token đã lưu và API lấy thông tin user.

### 2. Tra Cứu Và Dữ Liệu Từ Vựng

Phần dữ liệu từ vựng hiện hỗ trợ:

- Lấy chi tiết một từ theo `wordId`.
- Tìm từ theo text, dùng `normalized_word`.
- Autocomplete theo prefix của normalized text.
- Tìm từ theo category.
- Tìm từ theo level như `A1`, `A2`, `B1`, `B2`, `C1`, `C2`.
- Lấy danh sách từ theo category, có phân trang.
- Lấy danh sách từ theo level, có phân trang.
- Lấy danh sách category của từ vựng.

Một từ có thể có nhiều nghĩa. Với mỗi nghĩa có thể có:

- Định nghĩa gốc.
- Bản dịch/localization.
- Ví dụ theo đúng nghĩa đó.
- Bản dịch ví dụ.
- Từ đồng nghĩa, trái nghĩa, derived terms, coordinate terms, form-of, alt-of.
- Idiom và bản dịch idiom.
- Âm thanh phát âm.
- Category.

Backend có xử lý riêng cho nguồn dữ liệu `MOCHI`: một số từ không map theo luồng `words -> word_senses -> word_sense_localizations`, mà map trực tiếp xuống `word_sense_localizations` với `sense_id = null`.

Khi người dùng yêu cầu bản dịch mà ví dụ chưa có bản dịch, service có thể gọi Azure Translator, lưu lại vào DB rồi trả response.

### 3. Cache Redis Cho Word Data

Một số dữ liệu từ được cache để giảm query DB:

| Dữ liệu | Key |
|---|---|
| Word có bản dịch | `word_with_trans:<wordId>` |
| Word không có bản dịch | `word_without_trans:<wordId>` |
| User info | `user_info:<userId>` |
| Logout token | `logout:<token>` |
| Review session | `current_review:<userId>:<totalReviewVocab>:<langCode>` |
| Quiz review | `review_quiz:v2:<senseIdOrSenseLocalizedId>:<exerciseType>` |
| Chặn cộng nhiều lần khi sai | `current_review_wrong:<userVocabId>` |

Các key được cấu hình trong `src/main/resources/redis_keys.properties`.

### 4. Lưu Từ Vựng Cá Nhân

Người dùng có thể lưu một từ vào danh sách học. Khi lưu, backend kiểm tra trùng theo:

- `wordId + senseId`
- hoặc `wordId + senseLocalizedId`

Nếu đã tồn tại, API trả lỗi kèm message rõ phần nào bị trùng.

Khi lưu lần đầu:

- `level` mặc định là `1` nếu request không truyền.
- `currentLevelCorrectTurns = 0`.
- `nextReviewAt = now()`, nghĩa là từ mới có thể được đưa vào ôn tập ngay.

Người dùng cũng có thể:

- Lấy danh sách từ đã lưu theo level.
- Lấy chi tiết word từ một `userVocabId`.
- Ghi lịch sử tìm kiếm từ.
- Lấy lịch sử tìm kiếm.
- Xem attempts theo ngày.

### 5. Review Từ Vựng

Backend có luồng tạo bài ôn tập cho danh sách từ vựng của người dùng.

Các tổng số phiên review được chấp nhận:

- `30`
- `60`
- `90`

Các dạng quiz vocab hiện có:

| Exercise type | Ý nghĩa |
|---|---|
| `VOCAB_WORD_TO_MEANING` | Cho từ, chọn đúng nghĩa |
| `VOCAB_FILL_MISSING_WORD_PART` | Điền phần còn thiếu của từ/cụm |
| `VOCAB_LISTEN_AND_TYPE_WORD` | Nghe audio và nhập từ |
| `VOCAB_CHOOSE_WORD_IN_SENTENCE_BLANK` | Chọn từ đúng để điền vào câu |
| `VOCAB_FILL_WORD_IN_SENTENCE_BLANK` | Điền từ còn thiếu trong câu, có gợi ý ký tự |
| `VOCAB_MEANING_TO_SOUND` | Cho nghĩa, chọn đúng sound |
| `VOCAB_SENTENCE_TO_MEANING` | Cho câu có từ được nhấn mạnh, chọn đúng nghĩa |
| `VOCAB_SENTENCE_BLANK_TO_SOUND` | Cho câu khuyết từ, chọn sound đúng |

Khi tạo quiz, backend cố gắng không tạo trùng dạng bài trong cùng một lượt review. Với bài nghe, nếu từ không có sound thì dạng bài nghe được đánh dấu như đã xét và hệ thống lấy dạng khác.

Quiz được cache theo nghĩa đã lưu của người dùng, không chỉ theo `wordId`, để tránh nhầm giữa các nghĩa khác nhau của cùng một từ.

### 6. Submit Attempt Và Lịch Ôn Tập

Khi người dùng submit quiz:

- Backend lưu attempt vào `user_vocab_attempts`.
- Với exercise vocab, hệ thống cập nhật lịch ôn tập của `UserVocabulary`.
- Nếu trả lời sai nhiều lần trong cùng một lượt review, Redis được dùng để tránh cộng/trừ lịch review sai nhiều lần cho cùng một từ.

Các attempt đúng/sai được dùng cho phần thống kê. Hiện tại `correctQuizAttempt` và `wrongQuizAttempt` tính cả:

- Các quiz vocab có prefix `VOCAB_%`.
- Các bài listen-and-type có prefix `LAT_%`.

### 7. Listen-And-Type

Ứng dụng có một mảng luyện nghe dạng listen-and-type:

- Lấy danh sách category nghe.
- Lấy danh sách sub-category theo category.
- Lấy danh sách lesson theo sub-category.
- Lấy chi tiết một lesson.
- Ghi nhận lesson người dùng đã bắt đầu/làm.
- Lấy progress của người dùng trong lesson.

Sub-category có xử lý hiển thị thân thiện:

- `conversation_section_3` -> `Section 3`
- `short_stories_section_15` -> `Section 15`
- Các tên dạng `Cam 1`, `Cam 2`, `Cam 10` được sort theo số tự nhiên thay vì sort chuỗi.

Khi lấy danh sách lesson theo sub-category, response có:

- `totalPart`: tổng số challenge trong lesson.
- `completedPart`: số challenge người dùng đã hoàn thành.

Khi lấy chi tiết lesson, mỗi challenge có thêm:

- `isDone`: `true` nếu người dùng đã có attempt cho challenge đó, ngược lại `false`.

### 8. IELTS Reading Và Learning Resource

Hiện có API lấy danh sách IELTS Reading resource, có phân trang.

Ngoài ra backend có các API insert phục vụ nhập dữ liệu:

- IELTS reading source.
- Listen exercise.
- Listen-and-type quiz.
- Listen-and-answer quiz.
- Reading quiz.
- Generate reading/listening resource.

Một số phần trong nhóm này thiên về nhập dữ liệu/admin hơn là flow học phía người dùng cuối.

### 9. Notification

Backend có service gửi notification qua template:

- Email.
- Push notification qua Firebase.

Với verify email, nếu `notificationMethod = email` thì `recipientId` được hiểu trực tiếp là email nhận, không cần lookup user.

### 10. Insert Word Info

Project có nhóm API để thêm dữ liệu chi tiết cho word:

- Thêm category cho word.
- Thêm sense.
- Thêm sense localization.
- Thêm relation.
- Thêm example.
- Thêm example localization.
- Thêm idiom.
- Thêm idiom translation.
- Thêm sound.

Nhóm API này phù hợp cho tool nhập dữ liệu hoặc màn admin.

## Nhóm API Chính

Tất cả response được bọc bởi `ApiResponse<T>`:

```json
{
  "code": 2000,
  "message": "Success message",
  "traceId": "trace-id",
  "result": {}
}
```

### Auth

| Method | Path | Ghi chú |
|---|---|---|
| POST | `/auth/register` | Pre-register, gửi mã email |
| POST | `/auth/verify-email` | Xác thực email và tạo user |
| POST | `/auth/login` | Login, trả token/userId/username |
| POST | `/auth/logout` | Lưu token vào Redis blacklist |
| POST | `/auth/refresh-token` | Cấp token mới |
| POST | `/auth/forgot-password` | Gửi mã quên mật khẩu |
| POST | `/auth/forgot-password/submit-code` | Submit code quên mật khẩu |
| POST | `/auth/reset-password` | Đổi mật khẩu |

### User

| Method | Path | Ghi chú |
|---|---|---|
| GET | `/users/info` | Lấy thông tin user, ưu tiên Redis |
| PUT | `/users/info` | Cập nhật thông tin user |

### Word Data

| Method | Path | Ghi chú |
|---|---|---|
| GET | `/word-data/word` | Chi tiết word |
| GET | `/word-data/words/search` | Tìm word và map đủ dữ liệu |
| GET | `/word-data/words/basic-search` | Tìm và trả Word object cơ bản |
| GET | `/word-data/words/basic-search/by-category` | Tìm Word object theo category |
| GET | `/word-data/words/basic-search/by-level` | Tìm Word object theo level |
| GET | `/word-data/words/by-category` | Danh sách từ theo category, phân trang |
| GET | `/word-data/words/by-level` | Danh sách từ theo level, phân trang |
| GET | `/word-data/categories` | Danh sách category |
| GET | `/word-data/examples` | Lấy ví dụ |
| GET | `/word-data/idioms` | Lấy idiom |
| GET | `/word-data/forms` | Lấy form |
| GET | `/word-data/relations` | Lấy relation |
| GET | `/word-data/senses` | Lấy senses |

### User Vocabulary

| Method | Path | Ghi chú |
|---|---|---|
| POST | `/user-vocabularies` | Lưu từ vào danh sách học |
| POST | `/user-vocabularies/review-attempts` | Submit kết quả quiz |
| POST | `/user-vocabularies/search-history` | Lưu lịch sử tìm kiếm |
| GET | `/user-vocabularies/search-history` | Lấy lịch sử tìm kiếm |
| GET | `/user-vocabularies/attempts` | Lấy attempts theo ngày |
| GET | `/user-vocabularies/by-level` | Lấy từ đã lưu theo level |
| GET | `/user-vocabularies/statistics/daily` | Thống kê ngày |
| GET | `/user-vocabularies/statistics/overall` | Thống kê tổng |
| GET | `/user-vocabularies/{userVocabId}/word` | Lấy word detail theo từ đã lưu |

### Exercise

| Method | Path | Ghi chú |
|---|---|---|
| POST | `/exercises/user-lessons` | Thêm user lesson |
| GET | `/exercises/user-lessons/progress` | Progress trong lesson |
| GET | `/exercises/listen-and-type/categories` | Category nghe |
| GET | `/exercises/listen-and-type/sub-categories` | Sub-category nghe |
| GET | `/exercises/listen-and-type/lessons` | Lessons theo sub-category |
| GET | `/exercises/listen-and-type/lesson` | Chi tiết lesson |
| GET | `/exercises/vocab-review` | Lấy phiên review vocab |
| GET | `/exercises/vocab-review/word` | Lấy quiz cho một từ cụ thể |

### Learning Resource

| Method | Path | Ghi chú |
|---|---|---|
| GET | `/learning-resources/ielts-reading-sources` | IELTS Reading resources, phân trang |
| POST | `/learning-resources/ielts-reading-sources` | Thêm IELTS Reading source |
| POST | `/learning-resources/listen-exercises` | Thêm listen exercise |
| POST | `/learning-resources/quizzes/listen-and-type` | Thêm quiz listen-and-type |
| POST | `/learning-resources/quizzes/listen-and-answer` | Thêm quiz listen-and-answer |
| POST | `/learning-resources/quizzes/reading` | Thêm reading quiz |
| POST | `/learning-resources/quizzes/generate-reading-listening` | Generate resource |

### Word Info Admin/Input

| Method | Path |
|---|---|
| POST | `/word-info/categories` |
| POST | `/word-info/senses` |
| POST | `/word-info/sense-localizations` |
| POST | `/word-info/relations` |
| POST | `/word-info/examples` |
| POST | `/word-info/example-localizations` |
| POST | `/word-info/idioms` |
| POST | `/word-info/idiom-translations` |
| POST | `/word-info/sounds` |

### Notification

| Method | Path | Ghi chú |
|---|---|---|
| POST | `/notifications/send` | Gửi notification qua template |

## Response Và Error

Controller trả về cùng một format:

```json
{
  "code": 2000,
  "message": "Get word data successfully",
  "traceId": "a-trace-id",
  "result": {}
}
```

`traceId` được tạo qua filter và cũng được trả ở header `X-Trace-Id`, giúp debug theo từng request.

Một số lỗi nghiệp vụ phổ biến:

| Mã | Ý nghĩa |
|---:|---|
| 1001 | Unauthenticated |
| 2003 | User not found |
| 2008 | Invalid token |
| 2009 | Word not found |
| 2010 | Category not found |
| 2014 | User vocabulary not found |
| 2015 | Listen and type challenge not found |
| 2016 | Invalid exercise type |
| 2018 | Review vocab total must be 30, 60, or 90 |
| 2019 | All vocab exercise types were generated for this review session |
| 2020 | Word sound not found |
| 2021 | Lesson not found |
| 2023 | User vocabulary already exists |

## Cách Chạy Local

### 1. Chuẩn bị

Cần có:

- Java 21.
- Maven.
- MySQL.
- Redis.
- Database `vocab_app`.

### 2. Biến môi trường

Project đang đọc các biến sau:

```text
MYSQL_USERNAME
MYSQL_PASSWORD
REDIS_PASSWORD
JWT_SIGNER_KEY
SPRING_MAIL_USERNAME
SPRING_MAIL_PASSWORD
AZURE_TRANSLATOR_KEY_1
AZURE_TRANSLATOR_ENDPOINT
AZURE_TRANSLATOR_REGION
```

Nếu Redis local không có password, cần chỉnh lại cấu hình `spring.data.redis.password` trong `application.properties` cho phù hợp môi trường chạy.

### 3. Chạy project

```bash
mvn spring-boot:run
```

Hoặc build trước:

```bash
mvn clean package
```

### 4. Kiểm tra

```bash
mvn test
```

## Cấu Trúc Project

```text
src/main/java/com/bachdauduc/vocab_app
├── configuration   # Security, JWT, timezone, trace id, object mapper
├── constant        # Enum và constant nghiệp vụ
├── controller      # REST API
├── dto             # Request/response DTO
├── entity          # Entity map DB
├── exception       # ErrorCode và exception handler
├── properties      # Properties binding
├── repository      # JPA repository và native query
├── service         # Nghiệp vụ chính
└── utils           # Utility dùng chung
```

Một số file nên đọc khi phát triển tiếp:

- `docs/frontend_context.md`: tài liệu chi tiết cho frontend.
- `bussiness_rule.md`: rule nghiệp vụ.
- `app_schema_sumary.csv`: tóm tắt schema DB.
- `src/main/resources/redis_keys.properties`: rule đặt Redis key.
- `src/main/resources/application.properties`: cấu hình runtime.

## Ghi Chú Phát Triển

- Các API public/private được cấu hình trong `SecurityConfig`.
- Các response đều nên đi qua `ApiResponse`.
- Khi thêm cache Redis, nên thêm rule vào `redis_keys.properties`, tránh hard-code key rải rác.
- Với dữ liệu từ vựng, cần cẩn thận phân biệt `senseId` và `senseLocalizedId`; nhiều logic review/cache phụ thuộc đúng nghĩa mà người dùng đã lưu.
- Với word nguồn `MOCHI`, luồng mapping sense có khác so với word thường.
- Không nên hiển thị `solution` của listen challenge trực tiếp trên UI học, dù backend có trả về.
- Các field dạng JSON string như `jsonContent`, `synonyms`, `antonyms`, `hints` nên được frontend parse có kiểm soát, vì dữ liệu có thể đến từ nhiều nguồn.

## Trạng Thái Hiện Tại

Đã có nền khá đầy đủ cho một ứng dụng học từ vựng:

- Auth và user cơ bản.
- Word dictionary nhiều tầng dữ liệu.
- Lưu từ và ôn tập cá nhân.
- Review quiz nhiều dạng.
- Listen-and-type theo category/sub-category/lesson.
- Thống kê học tập.
- Redis cache cho các luồng nặng.
- Azure Translator cho dữ liệu dịch còn thiếu.
- Notification email/push.

Một vài phần còn có thể phát triển tiếp:

- API đăng ký push token cho thiết bị.
- Streak học tập.
- Giao diện/admin tool nhập dữ liệu.
- Test nghiệp vụ chi tiết hơn cho từng loại quiz và thống kê.
