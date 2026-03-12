# Lộ Trình Phát Triển

## Trạng Thái Hiện Tại: MVP Hoàn Thiện

### Giai Đoạn 1: Core Backend
- [x] Thiết kế database schema
- [x] Entity JPA và repository cho các bảng chính
- [x] REST controller cho sách, tài khoản, giỏ hàng, đơn hàng
- [x] Dockerfile và Docker Compose cho local stack

### Giai Đoạn 2: Xác Thực và Bảo Mật
- [x] Đăng ký tài khoản
- [x] Kích hoạt qua email
- [x] Đăng nhập JWT
- [x] BCrypt password hashing
- [x] Phân quyền `ADMIN` / `STAFF` / `USER`
- [x] Rate limiting đăng nhập
- [x] CORS và stateless session
- [x] Cấu hình JWT expiration qua env `JWT_EXPIRATION_MS`

### Giai Đoạn 3: Quản Lý Sách và Catalog
- [x] CRUD sách
- [x] Tìm kiếm, lọc theo thể loại, phân trang
- [x] API sách bán chạy, mới nhất, liên quan
- [x] SEO slug cho sách
- [x] SEO slug và routing cho thể loại
- [x] Admin CRUD thể loại với validate trùng tên/slug
- [x] Quản lý hình ảnh sách
- [x] Kích hoạt / vô hiệu hóa sách

### Giai Đoạn 4: Thương Mại và Đơn Hàng
- [x] Giỏ hàng
- [x] Đặt hàng có đăng nhập
- [x] Checkout bắt buộc chọn địa chỉ giao hàng (`maDiaChiGiaoHang`) và phương thức thanh toán (`phuongThucThanhToan`)
- [x] Quản lý địa chỉ giao hàng (`/api/dia-chi`) và FE route `/dia-chi`
- [x] Đặt hàng nhanh không đăng nhập
- [x] COD hoạt động như phương thức thanh toán first-class
- [x] Thanh toán VNPay sandbox (tạo URL theo `maDonHang`, backend tự tính tiền từ DB)
- [x] Email xác nhận đơn hàng
- [x] Cập nhật trạng thái giao hàng
- [x] DTO danh sách đơn hàng có thông tin phương thức thanh toán
- [x] Đồng bộ contract checkout BE/FE: request nhận `maCoupon`, response trả `tongTienSanPham`, `soTienGiam`, `maCoupon`

### Giai Đoạn 5: Đánh Giá, Admin, Coupon
- [x] Đánh giá sách
- [x] Admin kiểm duyệt bình luận
- [x] Admin quản lý người dùng và phân quyền
- [x] Admin thống kê tổng quan
- [x] CRUD coupon
- [x] Coupon validation giữ policy authenticated-only (`/api/coupon/kiem-tra`)
- [x] Mitigate race condition redeem coupon bằng atomic conditional update ở repository

### Giai Đoạn 6: Cloudinary Migration
- [x] Ngừng lưu base64 mới vào DB
- [x] Upload multipart ảnh sách lên Cloudinary
- [x] Lưu `cloudinary_public_id` để quản lý asset
- [x] Endpoint migrate ảnh legacy base64 theo batch
- [x] FE admin thêm/sửa sách đổi sang luồng upload file

### Giai Đoạn 7: Flyway Database Migration
- [x] Thay `ddl-auto=update` bằng Flyway migration
- [x] Tạo baseline schema (V1) từ 17 entity tables
- [x] Seed reference data (V2) và admin mặc định (V3)
- [x] Demo data (V4): 10 sách, 5 users, đơn hàng, đánh giá
- [x] Xóa `db/init/` scripts cũ
- [x] Cập nhật Docker Compose (bỏ initdb volume, đổi ddl-auto)
- [x] Cập nhật README và docs
- [x] Xác minh Docker build/runtime: backend + frontend build pass, `docker compose up` pass (frontend từ `../book_FE`)

## Mục Tiêu Tiếp Theo

### Bảo Mật
- [ ] Refresh token / re-auth flow rõ ràng hơn
- [ ] Global exception handler cho API
- [ ] Logging bảo mật và audit admin actions
- [ ] Rate limiting phân tán bằng Redis
- [ ] HTTPS và reverse proxy deployment guide

### Hiệu Năng
- [ ] Cache cho catalog và SEO
- [ ] Tối ưu index database
- [ ] Tối ưu truy vấn admin và dashboard
- [ ] Giảm phụ thuộc `open-in-view`

### Tính Năng
- [ ] Swagger / OpenAPI
- [ ] Quản lý kho hàng chi tiết hơn
- [ ] WebSocket / thông báo real-time
- [ ] Search nâng cao
- [ ] Workflow xóa asset Cloudinary khi xóa sách/ảnh

### Chất Lượng Mã Nguồn
- [ ] Unit tests và integration tests
- [ ] Constructor injection nhất quán
- [ ] Tách DTO response/request rõ ràng hơn
- [ ] Structured logging
- [ ] Chuẩn hóa validation thông điệp lỗi
