# Tổng Quan Dự Án - Web Bán Sách (Backend)

## Mục Đích

Hệ thống backend cho website thương mại điện tử bán sách trực tuyến, phục vụ người dùng Việt Nam.

## Phạm Vi

- API REST cho ứng dụng frontend React
- Quản lý sách, đơn hàng, giỏ hàng, người dùng
- Thanh toán trực tuyến qua VNPay
- Hệ thống xác thực JWT + phân quyền

## Đối Tượng Sử Dụng

| Vai trò | Mô tả |
|---------|-------|
| Khách (Guest) | Xem sách, tìm kiếm, đặt hàng không cần đăng nhập |
| Người dùng (USER) | Đăng nhập, mua sách, đánh giá, quản lý đơn hàng |
| Quản trị (ADMIN) | Quản lý toàn bộ: sách, người dùng, đơn hàng, bình luận, phân quyền |

## Tính Năng Chính

### 1. Xác Thực & Phân Quyền
- Đăng ký tài khoản + kích hoạt qua email
- Đăng nhập với JWT (hết hạn 30 phút)
- Rate limiting: khóa tạm 5 phút sau 5 lần đăng nhập sai
- 3 vai trò: ADMIN, STAFF, USER

### 2. Quản Lý Sách
- CRUD sách (tên, tác giả, mô tả, giá, số lượng, ISBN)
- Tìm kiếm theo tên, lọc theo thể loại
- Phân trang
- Kích hoạt/vô hiệu hóa sách
- Quản lý hình ảnh sách

### 3. Giỏ Hàng
- Thêm/xóa/cập nhật sản phẩm trong giỏ

### 4. Đơn Hàng
- Đặt hàng (có/không cần đăng nhập)
- Thanh toán trực tuyến VNPay
- Email xác nhận đơn hàng
- Theo dõi trạng thái giao hàng

### 5. Đánh Giá
- Người dùng đánh giá sách
- Admin kiểm duyệt (ẩn/hiện)

### 6. Quản Trị (Admin)
- Quản lý sách, người dùng, đơn hàng
- Kiểm duyệt bình luận
- Phân quyền người dùng

## Quyết Định Kỹ Thuật

| Quyết định | Lý do |
|-----------|-------|
| Spring Boot 3.3.4 | Framework phổ biến, hỗ trợ tốt cho REST API |
| MySQL 8.0 | RDBMS phù hợp cho e-commerce, hỗ trợ Unicode tiếng Việt |
| JWT (jjwt 0.11.5) | Xác thực stateless, phù hợp SPA frontend |
| VNPay Sandbox | Cổng thanh toán phổ biến tại Việt Nam |
| Lombok | Giảm boilerplate code |
| Docker | Đóng gói & triển khai nhất quán |

## Trạng Thái Hiện Tại

- Backend hoàn thiện các tính năng cốt lõi
- Frontend React (repo riêng `book_fe-master`)
- Docker compose cho 3 service: MySQL, Backend, Frontend
- Đang sử dụng VNPay sandbox (chưa production)
