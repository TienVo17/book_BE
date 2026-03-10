# Kiến Trúc Hệ Thống

## Tổng Quan

```
┌─────────────────┐     ┌──────────────────┐     ┌─────────────┐
│   Frontend      │     │    Backend        │     │   MySQL 8.0 │
│   React :3000   ├────►│  Spring Boot     ├────►│   :3306     │
│                 │HTTP  │    :8080         │ JPA │             │
└─────────────────┘     └──────────────────┘     └─────────────┘
                              │      │
                              │      │
                         ┌────┘      └────┐
                         ▼                ▼
                  ┌────────────┐   ┌────────────┐
                  │   VNPay    │   │   Gmail    │
                  │  Sandbox   │   │   SMTP    │
                  └────────────┘   └────────────┘
```

## Luồng Xác Thực (Authentication Flow)

```
Client                    Backend                      DB
  │                          │                          │
  ├──POST /tai-khoan/dang-ky─►│                          │
  │  {tenDangNhap, matKhau,  │──BCrypt hash password───►│
  │   email...}              │──Lưu NguoiDung──────────►│
  │                          │──Gửi email kích hoạt────►│ Gmail
  │◄─────200 OK──────────────┤                          │
  │                          │                          │
  ├──GET /tai-khoan/kich-hoat─►│                          │
  │  ?email=x&maKichHoat=y   │──Verify & activate──────►│
  │◄─────200 OK──────────────┤                          │
  │                          │                          │
  ├──POST /tai-khoan/dang-nhap►│                          │
  │  {username, password}    │──Rate limit check        │
  │                          │──AuthenticationManager   │
  │                          │  .authenticate()─────────►│
  │                          │──JwtService              │
  │                          │  .generateToken()        │
  │◄─────{jwt: "eyJ..."}────┤                          │
  │                          │                          │
  ├──GET /api/don-hang/findAll►│                          │
  │  Authorization: Bearer jwt│──JwtFilter              │
  │                          │  .extractUsername()      │
  │                          │  .validateToken()        │
  │                          │──SecurityContext set     │
  │                          │──Controller xử lý───────►│
  │◄─────Response────────────┤                          │
```

## Luồng Thanh Toán VNPay

```
Client              Backend              VNPay Sandbox
  │                    │                       │
  ├─GET /submitOrder──►│                       │
  │ ?amount&orderInfo  │──createOrder()        │
  │                    │  Tạo params VNPay     │
  │                    │  HMAC-SHA512 sign     │
  │◄──VNPay URL────────┤                       │
  │                    │                       │
  ├──Redirect to VNPay─────────────────────────►│
  │                    │                       │
  │◄──Redirect callback (vnp_ResponseCode)─────┤
  │                    │                       │
  ├─GET /vnpay-payment►│                       │
  │ ?vnp_ResponseCode  │──orderReturn()        │
  │ &vnp_Amount...     │  Verify checksum      │
  │                    │──Cập nhật đơn hàng───►│ DB
  │                    │──Gửi email xác nhận──►│ Gmail
  │◄──"ordersuccess"───┤                       │
```

## Luồng Đặt Hàng

```
Client                     Backend                          DB
  │                           │                              │
  │  (Đã đăng nhập)          │                              │
  ├──POST /api/don-hang/them─►│                              │
  │  Body: List<Sach>         │──SecurityContext.getAuth()   │
  │                           │──orderService.saveOrUpdate()─►│
  │                           │  Tạo DonHang                 │
  │                           │  Tạo ChiTietDonHang          │
  │◄──DonHang response────────┤                              │
  │                           │                              │
  │  (Không đăng nhập)        │                              │
  ├──POST /them-don-hang-moi──►│                              │
  │  ?hoTen&soDienThoai       │──Tạo DonHang (no user)──────►│
  │  &diaChiNhanHang          │                              │
  │◄──DonHang response────────┤                              │
```

## Phân Quyền API

### Endpoint Công Khai (Public)

| Method | Path | Mô tả |
|--------|------|-------|
| GET | `/sach/**`, `/hinh-anh/**` | Xem sách, hình ảnh |
| GET | `/api/sach**`, `/api/sach/search` | Tìm kiếm sách |
| GET | `/api/the-loai`, `/api/the-loai/{slug}` | Danh sách thể loại public và chi tiết theo slug |
| POST | `/tai-khoan/dang-ky` | Đăng ký |
| POST | `/tai-khoan/dang-nhap` | Đăng nhập |
| GET | `/tai-khoan/kich-hoat` | Kích hoạt tài khoản |
| ALL | `/gio-hang/**` | Thao tác giỏ hàng |
| GET | `/api/danh-gia/findAll**` | Xem đánh giá |
| GET | `/api/don-hang/vnpay-payment` | Callback VNPay |
| POST | `/api/don-hang/them-don-hang-moi` | Đặt hàng (guest) |

### Endpoint Yêu Cầu Đăng Nhập (Authenticated)

| Method | Path | Mô tả |
|--------|------|-------|
| POST | `/api/don-hang/them` | Đặt hàng |
| GET | `/api/don-hang/findAll**` | Xem đơn hàng |
| GET | `/api/don-hang/submitOrder**` | Tạo link VNPay |
| POST | `/api/danh-gia/them-danh-gia-v1` | Thêm đánh giá |

### Endpoint Admin

| Method | Path | Mô tả |
|--------|------|-------|
| POST | `/api/admin/sach/insert` | Thêm sách |
| PUT | `/api/admin/sach/update/**` | Sửa sách |
| POST | `/api/admin/sach/active/**` | Kích hoạt sách |
| POST | `/api/admin/sach/unactive/**` | Vô hiệu hóa sách |
| GET | `/api/admin/quyen/findAll` | Danh sách quyền |
| POST | `/api/admin/user/phan-quyen` | Phân quyền |
| GET | `/api/admin/the-loai` | Danh sách thể loại cho admin |
| POST | `/api/admin/the-loai` | Tạo thể loại mới, tự sinh slug |
| PUT | `/api/admin/the-loai/{maTheLoai}` | Cập nhật tên thể loại và slug |
| DELETE | `/api/admin/the-loai/{maTheLoai}` | Xóa thể loại khi chưa gắn sách |
| POST | `/api/don-hang/cap-nhat-trang-thai-giao-hang/**` | Cập nhật giao hàng |
| GET | `/api/admin/danh-gia/findAll**` | Xem đánh giá (admin) |
| POST | `/api/admin/danh-gia/active/**` | Duyệt đánh giá |
| POST | `/api/admin/danh-gia/unactive/**` | Ẩn đánh giá |

## Cấu Hình Bảo Mật

```
SecurityConfiguration
├── BCryptPasswordEncoder         # Hash mật khẩu
├── DaoAuthenticationProvider     # Xác thực từ DB
├── SecurityFilterChain
│   ├── Phân quyền endpoint (authorizeHttpRequests)
│   ├── CORS config (localhost:3000)
│   ├── JWT filter (trước UsernamePasswordAuthenticationFilter)
│   ├── Session: STATELESS
│   └── CSRF: disabled (dùng JWT)
└── AuthenticationManager
```

## Database Migration (Flyway)

```
App Startup:
  1. DataSource connect → MySQL
  2. Flyway check flyway_schema_history
  3. Flyway apply pending migrations (V1→V5)
  4. Hibernate validate schema vs entities
  5. Application context boot
```

- Schema quản lý bởi Flyway, **không** dùng `ddl-auto=update`
- Hibernate chỉ `validate` — phát hiện drift mà không tự sửa DB
- `V5__add_slug_to_the_loai.sql` thêm cột `slug`, backfill dữ liệu cũ, và tạo unique constraint cho bảng `the_loai`
- Demo data tự động seed khi DB trống

## Cấu Hình Docker

```yaml
Services:
  mysql:      MySQL 8.0, port 3306, empty root password
              Healthcheck: mysqladmin ping
              (Flyway tạo schema, không cần initdb scripts)

  backend:    Spring Boot (multi-stage Maven build)
              Port: 8080, depends_on: mysql (healthy)
              Env: DB_URL, DB_USER, DB_PASS, DDL_AUTO=validate

  frontend:   React build, port 3000
              depends_on: backend
```

## Biến Môi Trường

| Biến | Mặc định | Mô tả |
|------|---------|-------|
| `DB_URL` | `jdbc:mysql://localhost:3306/web_ban_sach` | JDBC URL |
| `DB_USERNAME` | `root` | DB username |
| `DB_PASSWORD` | (trống) | DB password |
| `JWT_SECRET` | Base64 encoded key | JWT signing key |
| `MAIL_USERNAME` | Gmail address | SMTP username |
| `MAIL_PASSWORD` | App password | SMTP password |
| `VNPAY_TMN_CODE` | `B3C4EVLT` | VNPay merchant code |
| `VNPAY_HASH_SECRET` | Sandbox key | VNPay secret key |
