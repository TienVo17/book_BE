# Web Bán Sách - Backend API

Hệ thống backend REST API cho website thương mại điện tử bán sách trực tuyến.

## Công Nghệ

- **Java 17** + **Spring Boot 3.3.4**
- **MySQL 8.0** + JPA/Hibernate
- **Spring Security** + JWT (jjwt 0.11.5)
- **VNPay** (thanh toán trực tuyến)
- **Spring Mail** (Gmail SMTP)
- **Lombok**, **Docker**

## Yêu Cầu

- Java 17+
- MySQL 8.0+
- Maven 3.9+

## Cài Đặt

### Cách 1: Docker (Khuyến nghị)

```bash
docker-compose up -d
```

Tự động khởi tạo:
- MySQL (port 3306) + seed data
- Backend (port 8080)
- Frontend (port 3000)

### Cách 2: Chạy thủ công

1. **Tạo database MySQL:**
```sql
CREATE DATABASE web_ban_sach CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

2. **Import dữ liệu mẫu:**
```bash
mysql -u root web_ban_sach < db/init/web_ban_sach.sql
mysql -u root web_ban_sach < db/init/zz-setup-admin-and-defaults.sql
```

3. **Cấu hình** (`src/main/resources/application.properties`):
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/web_ban_sach
spring.datasource.username=root
spring.datasource.password=
```

4. **Chạy ứng dụng:**
```bash
./mvnw spring-boot:run
```

Backend sẽ chạy tại `http://localhost:8080`

## Biến Môi Trường

| Biến | Mặc định | Mô tả |
|------|---------|-------|
| `DB_URL` | `jdbc:mysql://localhost:3306/web_ban_sach` | JDBC URL |
| `DB_USERNAME` | `root` | DB username |
| `DB_PASSWORD` | (trống) | DB password |
| `JWT_SECRET` | Base64 key | JWT signing key |
| `MAIL_USERNAME` | Gmail address | SMTP username |
| `MAIL_PASSWORD` | App password | SMTP password |
| `VNPAY_TMN_CODE` | Sandbox code | VNPay merchant code |
| `VNPAY_HASH_SECRET` | Sandbox key | VNPay secret key |

## API Endpoints

### Công khai
| Method | Path | Mô tả |
|--------|------|-------|
| POST | `/tai-khoan/dang-ky` | Đăng ký tài khoản |
| POST | `/tai-khoan/dang-nhap` | Đăng nhập (trả JWT) |
| GET | `/tai-khoan/kich-hoat` | Kích hoạt email |
| GET | `/api/sach?page=0` | Danh sách sách |
| GET | `/api/sach/search?tensach=x&page=0&size=8` | Tìm kiếm |
| GET | `/api/sach/{id}` | Chi tiết sách |

### Yêu cầu đăng nhập
| Method | Path | Mô tả |
|--------|------|-------|
| POST | `/api/don-hang/them` | Đặt hàng |
| GET | `/api/don-hang/findAll?page=0` | Đơn hàng của tôi |
| POST | `/api/danh-gia/them-danh-gia-v1` | Thêm đánh giá |

### Admin
| Method | Path | Mô tả |
|--------|------|-------|
| POST | `/api/admin/sach/insert` | Thêm sách |
| PUT | `/api/admin/sach/update/{id}` | Sửa sách |
| POST | `/api/admin/user/phan-quyen` | Phân quyền |

## Cấu Trúc Dự Án

```
src/main/java/com/example/book_be/
├── bo/           # Business Objects (DTO)
├── config/       # Cấu hình (REST, VNPay)
├── controller/   # REST Controllers
│   └── admin/    # Admin controllers
├── dao/          # JPA Repositories
├── entity/       # JPA Entities (14 bảng)
├── security/     # Spring Security + JWT config
└── services/     # Business logic
    ├── JWT/      # JWT service & filter
    ├── admin/    # Quản lý sách, user
    ├── cart/     # Giỏ hàng, đơn hàng
    ├── email/    # Gửi email
    └── review/   # Đánh giá
```

## Tài Liệu

Xem thêm trong thư mục `docs/`:
- [Tổng quan dự án](docs/project-overview-pdr.md)
- [Tóm tắt mã nguồn](docs/codebase-summary.md)
- [Tiêu chuẩn code](docs/code-standards.md)
- [Kiến trúc hệ thống](docs/system-architecture.md)
- [Lộ trình phát triển](docs/project-roadmap.md)

## Frontend

Frontend React nằm ở repo riêng: `../book_fe-master` (port 3000)
