# Lộ Trình Phát Triển

## Trạng Thái Hiện Tại: MVP Hoàn Thiện

### Giai Đoạn 1: Core Backend (Hoàn thành)
- [x] Thiết kế database schema (14 bảng)
- [x] Entity JPA với đầy đủ quan hệ
- [x] Spring Data REST auto-expose repositories
- [x] CORS configuration
- [x] Lombok integration

### Giai Đoạn 2: Xác Thực & Bảo Mật (Hoàn thành)
- [x] Đăng ký tài khoản
- [x] Kích hoạt qua email (Gmail SMTP)
- [x] Đăng nhập JWT (30 phút expiry)
- [x] BCrypt password hashing
- [x] Phân quyền ADMIN/STAFF/USER
- [x] Rate limiting đăng nhập (5 lần/5 phút)
- [x] Stateless session

### Giai Đoạn 3: Quản Lý Sách (Hoàn thành)
- [x] CRUD sách
- [x] Tìm kiếm theo tên
- [x] Lọc theo thể loại
- [x] Phân trang
- [x] Quản lý hình ảnh sách
- [x] Kích hoạt/vô hiệu hóa sách

### Giai Đoạn 4: Giỏ Hàng & Đơn Hàng (Hoàn thành)
- [x] Thêm/xóa/cập nhật giỏ hàng
- [x] Đặt hàng (có đăng nhập)
- [x] Đặt hàng nhanh (không đăng nhập)
- [x] Thanh toán VNPay (sandbox)
- [x] Email xác nhận đơn hàng
- [x] Cập nhật trạng thái giao hàng

### Giai Đoạn 5: Đánh Giá & Admin (Hoàn thành)
- [x] Đánh giá sách
- [x] Admin kiểm duyệt bình luận
- [x] Admin quản lý người dùng
- [x] Admin phân quyền
- [x] Admin quản lý đơn hàng

### Giai Đoạn 6: Docker & Deployment (Hoàn thành)
- [x] Dockerfile multi-stage build
- [x] docker-compose (MySQL + Backend + Frontend)
- [x] SQL init scripts cho Docker

## Cải Tiến Tiềm Năng

### Bảo Mật
- [ ] Chuyển VNPay sang production
- [ ] Refresh token mechanism
- [ ] Rate limiting phân tán (Redis thay ConcurrentHashMap)
- [ ] Input validation/sanitization chặt chẽ hơn
- [ ] HTTPS enforcement

### Hiệu Năng
- [ ] Caching (Redis/Caffeine)
- [ ] Database indexing optimization
- [ ] Connection pooling tuning (HikariCP)
- [ ] Lazy loading optimization

### Tính Năng
- [ ] Upload hình ảnh (hiện lưu URL/base64)
- [ ] Tìm kiếm nâng cao (Elasticsearch)
- [ ] Thông báo real-time (WebSocket)
- [ ] Quản lý kho hàng
- [ ] Báo cáo thống kê
- [ ] API documentation (Swagger/OpenAPI)

### Code Quality
- [ ] Unit tests & integration tests
- [ ] Constructor injection thay field injection
- [ ] Global exception handler (`@ControllerAdvice`)
- [ ] DTO pattern nhất quán (tách entity khỏi API response)
- [ ] API versioning
- [ ] Logging framework (SLF4J structured logging)
