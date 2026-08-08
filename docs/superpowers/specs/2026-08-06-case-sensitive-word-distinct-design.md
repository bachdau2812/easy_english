# Thiết kế DISTINCT phân biệt hoa/thường theo `word`

## Mục tiêu

Hai API `GET /words/basic-search` và `GET /word-data/words/search` tiếp tục tìm kiếm không phân biệt hoa/thường thông qua `normalized_word`, nhưng kết quả phải unique theo đúng bộ `(word, pos, source, level)`. Giá trị `word` được so sánh phân biệt hoa/thường, vì vậy `Apple` và `apple` là hai kết quả khác nhau khi các trường còn lại giống nhau.

## Nguyên nhân hiện tại

`WordRepository` đã nhóm theo `w2.word`, `w2.pos`, `w2.word_source`, `w2.cert_level`, nhưng MySQL áp dụng collation không phân biệt hoa/thường cho cột chuỗi. Vì vậy `GROUP BY w2.word` gộp `Apple` và `apple` trước khi dữ liệu được trả về service. Lớp service dùng Java record làm khóa dedup nên vốn đã phân biệt hoa/thường, nhưng không thể khôi phục dòng đã bị SQL loại bỏ.

## Thiết kế thay đổi

- Giữ nguyên điều kiện exact/prefix trên `normalized_word`; hành vi tìm kiếm không thay đổi.
- Trong hai native query exact và prefix, nhóm theo biểu diễn nhị phân của `word` cùng `pos`, `word_source`, `cert_level`.
- Nhánh tìm danh sách word duy nhất của `isUniqueSearch=true` cũng chọn đại diện theo `BINARY word`, để không gộp hai cách viết khác hoa/thường.
- Giữ lớp dedup phòng vệ trong `GetWordDataService` theo record `(word, pos, source, level)`; `String.equals` bảo toàn phân biệt hoa/thường.
- Không thay đổi DTO, tham số hoặc cấu trúc response của API.

## Luồng dữ liệu

1. Chuẩn hóa text đầu vào để truy vấn `normalized_word`.
2. Database lọc các từ phù hợp theo exact hoặc prefix.
3. Database chọn một ID đại diện cho mỗi bộ `(BINARY word, pos, word_source, cert_level)`.
4. Service dedup phòng vệ bằng chính bốn trường và map sang response hiện tại.

## Kiểm thử

- Thêm test repository với hai bản ghi trùng hoàn toàn cho `Apple` và hai bản ghi trùng hoàn toàn cho `apple`.
- Xác nhận exact search trả hai đại diện: một `Apple`, một `apple`.
- Xác nhận prefix/basic search cũng trả cả hai giá trị.
- Xác nhận các bản ghi trùng cùng cách viết vẫn chỉ trả một kết quả.
- Chạy test tập trung trước, sau đó chạy toàn bộ Maven test và `git diff --check`.

## Phạm vi không thay đổi

- Không đổi cách tạo hoặc lưu `normalized_word`.
- Không thay đổi quy tắc phân biệt hoa/thường của `pos`, `source` hoặc `level` ngoài hành vi collation hiện có.
- Không thay đổi các API tìm kiếm theo category/level.
