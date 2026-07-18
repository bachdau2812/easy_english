# Vocab App

Vocab App là backend Spring Boot cho ứng dụng học tiếng Anh tập trung vào từ vựng, luyện nghe, ôn tập thông minh và luyện IELTS. Mục tiêu của app là giúp người học tra cứu kỹ hơn, lưu đúng nghĩa cần học, luyện lại bằng nhiều dạng bài và theo dõi tiến độ theo từng lần làm bài.

## Chức Năng Chính

### Tra từ và học từ vựng

- Tra chi tiết một từ với nghĩa, ví dụ, bản dịch, phát âm, idiom, word form và các từ liên quan.
- Tìm kiếm từ cơ bản, tìm theo category hoặc CEFR level.
- Lưu từ theo đúng `senseId` hoặc `senseLocalizedId` để tránh học nhầm nghĩa.
- Lưu lịch sử tra cứu theo người dùng khi có `userId`.

### Ôn tập bằng quiz

- Tạo quiz ôn tập từ vựng theo nhiều dạng: chọn nghĩa, điền từ, nghe và nhập lại, chọn âm thanh đúng, chọn nghĩa theo câu ví dụ.
- Ghi nhận attempt, đáp án người dùng, trạng thái đúng/sai và số lần nghe lại.
- Thống kê theo ngày, tổng quan và danh sách từ hay sai.
- Hỗ trợ thêm trường `review` trong attempt để lưu nhận xét hoặc kết quả chấm cho các dạng bài cần phản hồi chi tiết.

### Luyện nghe Listen-and-Type

- Lấy category, sub-category và danh sách lesson.
- Mở lesson với audio, transcript, challenge theo từng đoạn và tiến độ đã hoàn thành.
- Nộp kết quả từng challenge bằng `ExerciseType.LAT_LISTEN_AND_TYPE`.

### IELTS Reading

- Lấy danh sách nguồn đọc IELTS Reading có phân trang.
- Lấy danh sách category IELTS Reading và lọc bài đọc theo category.
- Lấy quiz của một bài đọc theo `readingId`, gồm passage analysis, nhóm câu hỏi, câu hỏi, đáp án, evidence quote và explanation.
- Trả về `completed_question_ids` theo `userId` để frontend đánh dấu câu đã làm.
- Cache quiz trong Redis bằng key cấu hình trong `redis_keys.properties`.

### IELTS Writing và AI review

- Lấy topic IELTS Writing theo `taskType`.
- Lấy danh sách đề theo topic và trạng thái `isDone` theo người dùng.
- Lấy chi tiết đề, danh sách band tham khảo và bài mẫu theo band.
- Gửi bài viết để AI chấm qua Groq, nhận kết quả review dạng JSON string theo prompt đánh giá IELTS Writing.
- Lưu lịch sử review vào `user_vocab_attempts` với `ExerciseType.IELTS_WRITING_REVIEW`, gồm `exerciseId`, `userId`, `userAnswer` và `review`.

### Tài nguyên học tập và thông báo

- Có API cho learning resources, IELTS Reading source và các placeholder cho luồng tạo quiz/tài nguyên tiếp theo.
- Hỗ trợ gửi email và push notification qua notification template.

## API Nhanh

Base URL local:

```text
http://localhost:8080/vocab-learning
```

Tất cả response được bọc trong `ApiResponse<T>`:

```json
{
  "code": 2000,
  "message": "Success message",
  "traceId": "trace-id",
  "result": {}
}
```

### IELTS Reading

| Method | Endpoint | Mô tả |
|---|---|---|
| `GET` | `/learning-resources/ielts-reading-sources?page=0&limit=20` | Lấy danh sách bài đọc |
| `GET` | `/learning-resources/ielts-reading-sources/categories` | Lấy danh sách category |
| `GET` | `/learning-resources/ielts-reading-sources/by-category?name=Science&page=0&limit=20` | Lọc bài đọc theo category |
| `GET` | `/learning-resources/ielts-reading-sources/{readingId}/quiz?userId={userId}` | Lấy quiz và các câu đã hoàn thành |
| `POST` | `/learning-resources/ielts-reading-sources` | Thêm IELTS Reading source |

### IELTS Writing

| Method | Endpoint | Mô tả |
|---|---|---|
| `GET` | `/learning-resources/ielts-writing/topics?taskType=1` | Lấy topic theo task type |
| `GET` | `/learning-resources/ielts-writing/problems?topic_name=Education&userId={userId}` | Lấy danh sách đề theo topic |
| `GET` | `/learning-resources/ielts-writing/problems/{problemId}` | Lấy chi tiết đề |
| `GET` | `/learning-resources/ielts-writing/problems/{problemId}/bands` | Lấy các band bài mẫu |
| `GET` | `/learning-resources/ielts-writing/problems/{problemId}/references?band=7.0` | Lấy bài mẫu theo band |
| `POST` | `/learning-resources/ielts-writing/reviews` | Chấm bài viết bằng AI |
| `GET` | `/learning-resources/ielts-writing/attempt-history?userId={userId}&exerciseId={problemId}` | Lấy lịch sử chấm bài |

Request chấm IELTS Writing:

```json
{
  "exerciseId": "ielts-writing-exercise-id",
  "userId": "user-id",
  "userAnswer": "Learner essay..."
}
```

Response `result` là chuỗi JSON do AI trả về. Backend sẽ lưu chuỗi này vào trường `review` của attempt.

## Cấu Hình Và Chạy Local

Yêu cầu:

- Java 21
- MySQL với database `vocab_app`
- Redis
- Maven wrapper có sẵn trong repo

Các biến môi trường chính:

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
GROK_API_KEY
```

`GROK_API_KEY` được dùng cho tính năng AI review IELTS Writing qua cấu hình `grok.api.key`. Nếu chưa cấu hình key này, API review sẽ trả lỗi cấu hình.

Chạy test:

```powershell
.\mvnw.cmd test
```

Build jar:

```powershell
.\mvnw.cmd clean package
```

Chạy API:

```powershell
.\mvnw.cmd spring-boot:run
```

Sau khi chạy, Swagger UI nằm tại:

```text
http://localhost:8080/vocab-learning/swagger-ui.html
```

## Ghi Chú Bảo Mật

- Không commit secret hoặc file cấu hình riêng tư.
- Không log password, password hash, JWT, push token, bài viết dài của người dùng hoặc đáp án challenge nghe.
- Frontend không nên hiển thị `solution` của listen challenge cho người học dù backend có thể trả về trong response.
- Các endpoint không public cần gửi `Authorization: Bearer <token>`.
