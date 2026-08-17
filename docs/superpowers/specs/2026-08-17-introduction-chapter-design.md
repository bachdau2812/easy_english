# Thiết kế nội dung chương Giới thiệu

## Mục tiêu

Hoàn thiện `introduction.tex` theo văn phong báo cáo học thuật, giúp người đọc nắm được bối cảnh, mục tiêu, phạm vi chức năng và kiến trúc tổng quan của ứng dụng học tiếng Anh Vocab App trước khi đọc các chương chuyên môn.

## Phạm vi chỉnh sửa

1. Giữ nguyên phần `Thông tin chung`.
2. Thêm phần `Giới thiệu dự án` ngay sau phần thông tin chung.
3. Đổi tiêu đề `Một số công nghệ sử dụng/tìm hiểu được trong quá trình thực tập` thành `Một số công nghệ sử dụng`.
4. Bổ sung hai mục công nghệ `Docker` và `ReactJS`.
5. Hiệu chỉnh các mục Java Spring Boot, Redis, MySQL và Linux để nội dung gắn với Vocab App thay vì mô tả chung về sản phẩm của công ty.

## Nội dung giới thiệu dự án

Phần giới thiệu dự án được trình bày theo trình tự:

- Bối cảnh: nhu cầu học từ vựng theo đúng nghĩa, ôn tập có hệ thống và kết hợp luyện các kỹ năng tiếng Anh.
- Mục tiêu: xây dựng nền tảng hỗ trợ tra cứu, lưu từ, ôn tập, luyện nghe, IELTS Reading và IELTS Writing.
- Đối tượng sử dụng: người học tiếng Anh, đặc biệt là người cần mở rộng vốn từ và luyện thi IELTS.
- Chức năng chính: tra cứu và lưu từ theo nghĩa; quiz ôn tập; thống kê tiến độ; Listen-and-Type; IELTS Reading; IELTS Writing có phản hồi AI; tài nguyên học tập và thông báo.
- Kiến trúc tổng quan: ReactJS đảm nhiệm giao diện; Spring Boot cung cấp REST API và nghiệp vụ; MySQL lưu dữ liệu lâu dài; Redis hỗ trợ cache và trạng thái tạm thời; Docker đóng gói backend để triển khai trên Linux; dịch vụ ngoài hỗ trợ dịch thuật, AI, email và thông báo đẩy.

Phần này mô tả đầy đủ bối cảnh nhưng không đi sâu vào endpoint, bảng dữ liệu hoặc chi tiết cài đặt vì những nội dung đó thuộc các chương phân tích và triển khai.

## Nội dung phần công nghệ

Mỗi công nghệ sử dụng cùng một cấu trúc: giới thiệu ngắn, bối cảnh hoặc vai trò trong ứng dụng, ưu điểm và hạn chế. Các nhận định phải cụ thể, trung tính và tránh từ ngữ tuyệt đối.

- Java Spring Boot: nền tảng backend, REST API, bảo mật, truy cập dữ liệu và tích hợp dịch vụ.
- Redis: cache dữ liệu truy cập thường xuyên và hỗ trợ dữ liệu/trạng thái có vòng đời ngắn.
- MySQL: cơ sở dữ liệu quan hệ chính cho người dùng, từ vựng, bài học, đề luyện tập và lịch sử làm bài.
- Linux: môi trường máy chủ vận hành ứng dụng và container.
- Docker: đóng gói backend cùng môi trường chạy Java, tạo ảnh nhất quán cho CI/CD và triển khai.
- ReactJS: xây dựng giao diện theo component, quản lý trạng thái hiển thị và giao tiếp với REST API; chỉ giới thiệu công nghệ, không mô tả chi tiết mã nguồn frontend.

## Tiêu chí hoàn thành

- File LaTeX có cấu trúc hợp lệ, tiêu đề đúng yêu cầu và có đủ sáu công nghệ.
- Nội dung giới thiệu phản ánh đúng các chức năng đang được mô tả trong `README.md` và mã nguồn backend.
- Không đưa thông tin bí mật, cấu hình nhạy cảm hoặc khẳng định không có căn cứ.
- Thuật ngữ tiếng Việt và tiếng Anh được dùng nhất quán, văn phong phù hợp báo cáo thực tập.
