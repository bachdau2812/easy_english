# User Vocabulary Info API Design

## Mục tiêu

Bổ sung một API tổng hợp dữ liệu cho màn hình My Vocab. API nhận `userId` và
`infoType`, sau đó chỉ truy vấn loại dữ liệu được yêu cầu.

Các API user vocabulary hiện tại được giữ nguyên.

## Endpoint

```http
GET /user-vocabularies/info?userId={userId}&infoType={infoType}
```

Hai giá trị `infoType` hợp lệ:

- `VOCAB_QUANTITY`
- `VOCAB_REVIEW`

Controller tiếp tục trả response qua `ApiResponse<UserVocabularyInfoResponse>`.

## Response chung

`UserVocabularyInfoResponse` có các trường:

- `userId: String`
- `infoType: UserVocabularyInfoType`
- `totalQuantity: Long`
- `quantityByLevels: List<UserVocabularyLevelQuantityResponse>`
- `reviewQuantity: Long`

`UserVocabularyLevelQuantityResponse` có:

- `level: Integer`
- `quantity: Long`

Các trường không áp dụng với `infoType` hiện tại được trả về dưới dạng `null`.

### VOCAB_QUANTITY

```json
{
  "userId": "user-1",
  "infoType": "VOCAB_QUANTITY",
  "totalQuantity": 120,
  "quantityByLevels": [
    { "level": 1, "quantity": 30 },
    { "level": 2, "quantity": 25 },
    { "level": 3, "quantity": 20 },
    { "level": 4, "quantity": 15 },
    { "level": 5, "quantity": 20 },
    { "level": 6, "quantity": 10 }
  ],
  "reviewQuantity": null
}
```

Quy tắc:

- Luôn trả đủ và đúng thứ tự level 1 đến 6.
- Level chưa có vocab trả `quantity = 0`.
- `totalQuantity` là tổng tất cả row `user_vocabularies` của user.
- Nếu dữ liệu bất thường có level ngoài 1–6, row đó vẫn được tính vào
  `totalQuantity`, nhưng không tạo thêm phần tử ngoài danh sách level 1–6.

### VOCAB_REVIEW

```json
{
  "userId": "user-1",
  "infoType": "VOCAB_REVIEW",
  "totalQuantity": null,
  "quantityByLevels": null,
  "reviewQuantity": 18
}
```

`reviewQuantity` đếm các row thỏa:

```sql
user_id = :userId
AND next_review_at <= :now
```

`next_review_at IS NULL` không được tính.

## Kiến trúc

### Enum

Thêm `UserVocabularyInfoType` trong package `constant` để định nghĩa hai giá
trị hợp lệ.

Controller nhận `infoType` dạng String và để service chuẩn hóa bằng
`trim().toUpperCase()`. Cách này cho phép trả business error nhất quán thay vì
phụ thuộc lỗi enum conversion mặc định của Spring.

### Controller

`UserVocabularyController` thêm method `GET /info`. Method chỉ log request,
gọi `UserVocabularyService` và bọc kết quả trong `ApiResponse`.

`/info` là static route và không xung đột với route
`/{userVocabId}/word`.

### Service

`UserVocabularyService.getUserVocabularyInfo(userId, infoType)`:

1. Kiểm tra user tồn tại bằng rule hiện hữu.
2. Parse và validate `infoType`.
3. Với `VOCAB_QUANTITY`, đọc aggregate theo level, dựng map và tạo đủ sáu
   phần tử level; tổng được tính từ toàn bộ aggregate rows.
4. Với `VOCAB_REVIEW`, gọi một count query với `LocalDateTime.now()`.
5. Không load entity list và không gọi cả hai query trong cùng request.

### Repository

`UserVocabularyRepository` bổ sung:

- Aggregate query group theo `level`, trả projection gồm `level` và
  `quantity`.
- Count query cho `userId` và `nextReviewAt <= now`.

Không thay đổi database schema và không cần migration.

## Error handling

- User không tồn tại: giữ nguyên `AppException(ErrorCode.USER_NOT_FOUND)`.
- `infoType` null, blank hoặc khác hai giá trị hỗ trợ: thêm error code
  `INVALID_USER_VOCABULARY_INFO_TYPE`, HTTP 400.
- User chưa lưu vocab: trả số lượng tổng/review bằng 0 và danh sách level 1–6
  đều bằng 0; không trả 404.

## Cache

Không thêm Redis cache. Dữ liệu thay đổi khi user thêm vocab hoặc submit review
attempt; query aggregate/count trực tiếp đảm bảo kết quả mới nhất và tránh thêm
luồng invalidation.

## Testing

Thêm test tập trung cho:

- `VOCAB_QUANTITY`: đủ level 1–6, điền 0, tính đúng tổng và không gọi review
  count query.
- `VOCAB_REVIEW`: trả đúng count và không gọi aggregate query.
- User không tồn tại.
- `infoType` không hợp lệ.
- Controller mapping và `ApiResponse` contract.

Chạy toàn bộ `mvnw test` và `mvnw clean package` trước khi bàn giao.
