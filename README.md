# Web Bán Sách - Backend API

Backend REST API cho website thương mại điện tử bán sách trực tuyến.

## Công Nghệ

- Java 17 + Spring Boot 3.3.4
- MySQL 8.0 + Spring Data JPA/Hibernate
- Spring Security + JWT
- Cloudinary cho lưu trữ hình ảnh sách
- VNPay cho thanh toán trực tuyến
- Spring Mail (Gmail SMTP)
- Docker Compose cho local stack

## Yêu Cầu

- Java 17+
- Maven 3.9+
- MySQL 8.0+ hoặc Docker Desktop

## Chạy Bằng Docker

```bash
docker compose up --build -d
```

Stack khởi tạo:

- `mysql` trên port `3306`
- `backend` trên port `8080`
- `frontend` từ repo `../book_FE` trên port `3000`

## Chạy Thủ Công

1. Tạo database rỗng:

```sql
CREATE DATABASE web_ban_sach CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

2. Cấu hình biến môi trường hoặc `application.properties` (xem bảng bên dưới)

3. Chạy ứng dụng:

```bash
./mvnw spring-boot:run
```

Flyway sẽ tự động tạo schema, seed dữ liệu tham chiếu (quyền, hình thức giao hàng/thanh toán), và tài khoản admin mặc định.

Backend mặc định tại `http://localhost:8080`.

> **Lưu ý:** Thư mục `db/init/` chỉ để tham chiếu lịch sử, **không cần import thủ công** nữa.
> Schema được quản lý bởi Flyway tại `src/main/resources/db/migration/`.

## Biến Môi Trường

| Biến | Mặc định | Mô tả |
|------|----------|-------|
| `DB_URL` | `jdbc:mysql://localhost:3306/web_ban_sach` | JDBC URL |
| `DB_USERNAME` | `root` | DB username |
| `DB_PASSWORD` | rỗng | DB password |
| `JWT_SECRET` | Base64 key | JWT signing key |
| `JWT_EXPIRATION_MS` | `28800000` | Thời gian hết hạn JWT, mặc định 8 giờ |
| `MAIL_USERNAME` | rỗng | SMTP username |
| `MAIL_PASSWORD` | rỗng | SMTP password |
| `VNPAY_TMN_CODE` | rỗng | VNPay merchant code |
| `VNPAY_HASH_SECRET` | rỗng | VNPay secret |
| `CLOUDINARY_URL` | rỗng | Chuỗi kết nối Cloudinary |
| `FRONTEND_URL` | `http://localhost:3000` | Base URL frontend cho SEO |

## Luồng Ảnh Sách

Hệ thống đã chuyển sang lưu URL ảnh trên Cloudinary thay vì lưu base64 mới trong database.

- Thêm/sửa sách có thể đồng bộ danh sách URL ảnh còn lại qua `listImageStr`
- Upload file mới qua endpoint multipart `POST /api/admin/sach/{id}/hinh-anh`
- Có endpoint admin migrate dữ liệu legacy base64: `POST /api/admin/sach/migrate-hinh-anh-base64?limit=20`
- Bảng `hinh_anh` lưu thêm `cloudinary_public_id` để hỗ trợ xóa/thay thế asset

## API Chính

### Public

- `POST /tai-khoan/dang-ky`
- `POST /tai-khoan/dang-nhap`
- `GET /tai-khoan/kich-hoat`
- `GET /api/sach`
- `GET /api/sach/search`
- `GET /api/sach/{id}`
- `GET /api/sach/ban-chay`
- `GET /api/sach/moi-nhat`
- `GET /api/sach/{id}/lien-quan`
- `GET /api/seo/**`

### Authenticated

- `POST /api/don-hang/them`
- `GET /api/don-hang/findAll**`
- `GET /api/don-hang/submitOrder**`
- `POST /api/danh-gia/them-danh-gia-v1`

### Admin

- `POST /api/admin/sach/insert`
- `PUT /api/admin/sach/update/{id}`
- `POST /api/admin/sach/{id}/hinh-anh`
- `POST /api/admin/sach/migrate-hinh-anh-base64`
- `POST /api/admin/sach/active/{id}`
- `POST /api/admin/sach/unactive/{id}`
- `GET /api/admin/thong-ke`
- `POST /api/admin/user/phan-quyen`
- `GET /api/admin/coupon/**`
- `POST /api/admin/coupon`
- `PUT /api/admin/coupon/**`
- `DELETE /api/admin/coupon/**`

## Tài Liệu

Xem thêm trong thư mục `docs/`:

- `docs/project-overview-pdr.md`
- `docs/codebase-summary.md`
- `docs/code-standards.md`
- `docs/system-architecture.md`
- `docs/project-roadmap.md`

## Frontend

Frontend React nằm ở repo riêng: `../book_FE`.
Docker Compose trong repo backend sẽ build frontend từ đường dẫn này.
