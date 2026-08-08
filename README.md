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

## Các Dạng Quiz Trong `ExerciseType`

`ExerciseType` xác định loại bài tập được trả về cho frontend và loại attempt được lưu trong bảng `user_vocab_attempts`.

### Quiz ôn tập từ vựng (`VOCAB_*`)

| ExerciseType | Mô tả | Cách trả lời và dữ liệu chính |
|---|---|---|
| `VOCAB_WORD_TO_MEANING` | Hiển thị một từ và yêu cầu chọn nghĩa đúng. | `listAnswers` chứa tối đa 4 nghĩa; `correctAnswer` là nội dung nghĩa đúng. |
| `VOCAB_FILL_MISSING_WORD_PART` | Che ngẫu nhiên một số ký tự trong từ và yêu cầu điền phần còn thiếu. | `maskedWord` là từ đã che; `metadata` ánh xạ vị trí ký tự sang ký tự đúng; `correctAnswer` là từ đầy đủ. Từ có không quá 2 chữ cái không dùng dạng này. |
| `VOCAB_LISTEN_AND_TYPE_WORD` | Phát âm thanh của từ và yêu cầu người học nhập lại từ nghe được. | Phát `audioUrl`; `correctAnswer` là từ đầy đủ. Chỉ sinh quiz khi có âm thanh phù hợp. |
| `VOCAB_CHOOSE_WORD_IN_SENTENCE_BLANK` | Khuyết một từ trong câu ví dụ và yêu cầu chọn từ phù hợp. | `sentence` chứa vị trí khuyết tại `missIndex`; `listAnswers` chứa tối đa 4 lựa chọn; `correctAnswer` là từ đúng; `trans` có thể chứa bản dịch câu. |
| `VOCAB_FILL_WORD_IN_SENTENCE_BLANK` | Đưa từ bị che một phần vào vị trí khuyết của câu và yêu cầu hoàn thiện từ. | Dùng `sentence`, `missIndex`, `maskedWord` và `metadata`; `correctAnswer` là từ đầy đủ. Từ có không quá 2 chữ cái không dùng dạng này. |
| `VOCAB_MEANING_TO_SOUND` | Cho nghĩa/ngữ cảnh của từ và yêu cầu chọn âm thanh phát âm đúng. | `metadata` chứa các lựa chọn âm thanh với khóa từ `1` đến `4`; `correctAnswer` là khóa của lựa chọn đúng ở dạng chuỗi. Có thể kèm `sentence` và `trans`. |
| `VOCAB_SENTENCE_TO_MEANING` | Hiển thị câu ví dụ có từ mục tiêu được đánh dấu và yêu cầu chọn nghĩa đúng của từ trong ngữ cảnh. | `metadata` chứa các nghĩa với khóa từ `1` đến `4`; `correctAnswer` là khóa đúng ở dạng chuỗi; `sentence`, `missIndex` và `trans` cung cấp ngữ cảnh. |
| `VOCAB_SENTENCE_BLANK_TO_SOUND` | Khuyết từ mục tiêu trong câu và yêu cầu chọn âm thanh tương ứng với từ đúng. | `metadata` chứa các lựa chọn âm thanh với khóa từ `1` đến `4`; `correctAnswer` là khóa đúng ở dạng chuỗi; câu khuyết được trả qua `sentence` và `missIndex`. |

Các dạng `VOCAB_*` được tạo bởi API `GET /exercises/vocab-review` hoặc `GET /exercises/vocab-review/word`. Khi nộp kết quả qua `POST /user-vocabularies/review-attempts`, request phải có `userVocabId`. Chỉ nhóm này cập nhật level, số lượt đúng và `nextReviewAt` của từ đã lưu.

Bốn dạng `VOCAB_CHOOSE_WORD_IN_SENTENCE_BLANK`, `VOCAB_FILL_WORD_IN_SENTENCE_BLANK`, `VOCAB_SENTENCE_TO_MEANING` và `VOCAB_SENTENCE_BLANK_TO_SOUND` cần câu ví dụ. Trước khi sinh quiz, backend kiểm tra theo từng cặp `word_id/sense_id` của từ thông thường và bảo đảm có tối thiểu 4 câu ví dụ khác nhau. Nếu đang có từ 0 đến 3 câu, backend gom các sense thiếu vào một batch Groq và chỉ yêu cầu số câu còn thiếu để đạt 4. Mỗi kết quả AI hợp lệ được lưu đồng thời vào `word_examples` và `word_example_localizations`; bản dịch dùng `lang_code=vi`, `review_status=1`. Từ MOCHI dùng `sense_localized_id` đã có dữ liệu riêng nên không được gửi sang Groq.

Việc sinh ví dụ là best-effort: lỗi cấu hình, lỗi Groq hoặc kết quả không hợp lệ không làm hỏng toàn bộ API ôn tập. Nếu một trong bốn dạng trên vẫn không có ví dụ, backend đánh dấu riêng dạng đó là đã xét trong phiên hiện tại và thử một dạng quiz khác.

### Quiz nghe, đọc và viết

| ExerciseType | Mô tả | Cách lưu attempt |
|---|---|---|
| `LAT_LISTEN_AND_TYPE` | Nghe một đoạn âm thanh trong lesson Listen-and-Type rồi nhập lại nội dung nghe được. | Gửi `attemptId` bằng ID của challenge, `userAnswer`, `correct` và `replayCount`. Loại này dùng để tính tiến độ lesson, không cập nhật lịch ôn từ vựng. |
| `QUIZ_IELTS_READING` | Trả lời một câu hỏi thuộc quiz IELTS Reading. | Lưu loại attempt thuộc nhóm `QUIZ_*`; `attemptId` dùng để nhận diện câu hỏi đã làm và `correct` ghi nhận kết quả. |
| `IELTS_WRITING_REVIEW` | Gửi bài IELTS Writing để AI đánh giá và lưu lại kết quả review. | Backend tự lưu `attemptId` bằng ID đề viết, `userAnswer` là bài làm và `review` là kết quả đánh giá; attempt được đánh dấu `correct=true`. Loại này không được nộp qua API review-attempt thông thường. |

### Quy ước phân nhóm

- `isVocab()` trả về `true` cho tên bắt đầu bằng `VOCAB_`.
- `isListenAndType()` trả về `true` cho tên bắt đầu bằng `LAT_`.
- `isQuiz()` trả về `true` cho tên bắt đầu bằng `QUIZ_`.
- Trường `correct` hiện do frontend tính và gửi lên khi nộp attempt; backend không tự đối chiếu lại `userAnswer` với `correctAnswer` trong API nộp bài.

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
GROQ_API_KEY
```

`GROQ_API_KEY` được dùng cho AI review IELTS Writing và sinh câu ví dụ còn thiếu cho quiz từ vựng. Cấu hình cũ `GROK_API_KEY` vẫn được hỗ trợ làm fallback để tương thích ngược.

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
