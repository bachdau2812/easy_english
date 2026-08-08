# Vocab review quiz implementation

## Phạm vi

Hai API giữ nguyên request/response contract:

- `GET /exercises/vocab-review`
- `GET /exercises/vocab-review/word`

Luồng vẫn đồng bộ. Nếu một sense chưa đủ example, bước Groq preflight vẫn chạy và hoàn tất trước khi trả quiz.

## Luồng batch

1. Xác thực user và chọn các `UserVocabulary` đến hạn theo quota level hiện có.
2. Gọi `WordExampleGenerationService.ensureExamples` một lần cho toàn bộ vocab được chọn.
3. Tạo identity `word + sense + lang` và đọc revision của các word bằng một Redis MGET.
4. Đọc tất cả snapshot bằng một Redis MGET thứ hai.
5. Với cache miss, bulk-load words, senses, localizations, sounds, examples và example translations từ database.
6. Ghi các snapshot miss bằng một Redis pipeline.
7. Tạo một `ReviewRequestContext` dùng chung cho toàn request, bao gồm các distractor pool đã deduplicate.
8. Xác định những dạng quiz khả dụng cho từng từ dựa trên dữ liệu thực tế.
9. `BalancedReviewQuizScheduler` phân bổ loại quiz đều trên toàn batch; loại bị thiếu dữ liệu được tái phân bổ cho các từ khác.
10. Lua script nguyên tử loại những dạng đã dùng với cùng user/word trong 2 giờ và reserve dạng đầu tiên còn trống.
11. `ReviewQuizFactory` tạo response hoàn toàn trong bộ nhớ, không query database/Redis thêm.

## Luồng lấy thêm quiz cho một từ trả lời sai

1. Kiểm tra `userVocabId` thuộc user hiện tại.
2. Chạy Groq preflight đồng bộ cho đúng target.
3. Chỉ lấy tối đa 32 vocab đến hạn làm distractor context và luôn thêm target nếu target không nằm trong danh sách đó.
4. Thực hiện cùng chuỗi snapshot cache, bulk-load, eligibility, progress reservation và quiz factory như batch.
5. Chỉ target được sinh quiz; các vocab còn lại chỉ cung cấp distractor.

## Quy tắc cân bằng

- Có 8 dạng `VOCAB_*`.
- Với batch có đủ dữ liệu, số lượng mỗi dạng chênh nhau tối đa một.
- Target có ít dạng khả dụng được xếp trước để tránh mất slot.
- Khi dạng đã phân bổ không còn dùng được cho user/word vì rule 2 giờ, Redis Lua chọn dạng khả dụng tiếp theo.
- Các candidate fallback được sắp theo số quiz thực tế đã emit trong request để tiếp tục giữ batch cân bằng.

## Redis

### Key đang dùng

| Pattern | Kiểu | TTL | Mục đích |
|---|---|---:|---|
| `review_vocab_revision:v1:{wordId}` | String counter | Không đặt TTL | Version hóa snapshot; tăng sau commit khi dữ liệu word/sense/example/sound thay đổi |
| `review_vocab_snapshot:v1:{wordId}:{senseKey}:{langCode}:{revision}` | JSON String | 6 giờ + jitter 0–30 phút | Shared snapshot không chứa user data |
| `review_progress:v2:{userId}:{wordId}` | Sorted Set | key cleanup 3 giờ | Member là `ExerciseType`, score là thời điểm reservation hết hạn |
| `current_review_wrong:{userVocabId}` | Key hiện hữu | 2 giờ | Giữ nguyên cho luồng ghi nhận câu trả lời sai |

Snapshot không chứa `userId`, `userVocabId`, progress cá nhân hoặc final quiz response.

### Key ngừng dùng

- `review_quiz:v2:*`: không cache final response nữa vì response chứa `userVocabId` và phụ thuộc request distractors/language.
- `current_review:*`: được thay bằng một sorted set `review_progress:v2:*` và một Lua call nguyên tử.

Không chạy lệnh xóa dữ liệu Redis khi deploy. Các key cũ tự hết hạn theo TTL 2 giờ đã có.

## Invalidation và fallback

- Các writer của sense, sense localization, example, example localization và sound đăng ký tăng revision sau transaction commit.
- Example do Groq sinh cũng tăng revision sau commit, trước khi request tiếp tục đọc snapshot.
- Redis read/write/invalidation lỗi không làm hỏng request: loader fallback về database và snapshot vẫn được giới hạn stale bởi TTL.
- Có thể tắt snapshot cache bằng `review.snapshot-cache.enabled=false`.

## Cấu hình hiệu năng

- Hibernate JDBC batch size: 50.
- Hibernate sắp xếp insert/update để tăng khả năng batching.
- Snapshot write dùng Redis pipeline; snapshot/revision read dùng MGET.
