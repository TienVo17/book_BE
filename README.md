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
- Biến `JWT_SECRET` do môi trường runtime cung cấp, là khóa ký Base64 hợp lệ

## Chạy Bằng Docker

Thiết lập `JWT_SECRET` trước khi khởi động. `docker-compose.yml` yêu cầu biến này, không tự tạo khóa JWT.

```bash
export JWT_SECRET='<base64-signing-key>'
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

2. Cấu hình biến môi trường hoặc `application.properties` (xem bảng bên dưới), bao gồm `JWT_SECRET` Base64.

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
| `PORT` | `8080` | HTTP port của Spring Boot |
| `DB_URL` | `jdbc:mysql://localhost:3306/web_ban_sach` | JDBC URL ưu tiên khi được đặt |
| `DB_HOST`, `DB_PORT`, `DB_NAME` | `localhost`, `3306`, `web_ban_sach` | Thành phần JDBC fallback khi không đặt `DB_URL` |
| `DB_USERNAME` / `DB_USER` | `root` | DB username (`DB_USERNAME` ưu tiên) |
| `DB_PASSWORD` / `MYSQL_PASSWORD` | rỗng | DB password (`DB_PASSWORD` ưu tiên) |
| `FLYWAY_CONNECT_RETRIES` | `10` | Số lần Flyway retry khi database chưa sẵn sàng |
| `FLYWAY_CONNECT_RETRIES_INTERVAL` | `5` | Số giây giữa các lần retry |
| `JWT_SECRET` | bắt buộc | Khóa ký JWT Base64 do môi trường runtime cấp |
| `JWT_EXPIRATION_MS` | `28800000` | JWT expiration in milliseconds (mặc định 8 giờ) |
| `MAIL_USERNAME` | rỗng | SMTP username |
| `MAIL_PASSWORD` | rỗng | SMTP password |
| `VNPAY_TMN_CODE` | rỗng | VNPay merchant code |
| `VNPAY_HASH_SECRET` | rỗng | VNPay secret |
| `VNPAY_PAY_URL` | VNPay sandbox payment URL | URL cổng thanh toán HTTP(S), không có query/fragment |
| `VNPAY_API_URL` | VNPay sandbox transaction API | URL API giao dịch HTTP(S), không có query/fragment |
| `VNPAY_RETURN_URL` | `FRONTEND_URL` + `/xu-ly-kq-thanh-toan` | URL browser return từ VNPay; override khi cần |
| `CLOUDINARY_URL` | rỗng | Chuỗi kết nối Cloudinary |
| `FRONTEND_URL` | `http://localhost:3000` | Origin frontend duy nhất cho CORS, email links và VNPay return mặc định |

`FRONTEND_URL` phải là HTTP(S) origin tuyệt đối không có path, query, hay fragment; slash cuối được chuẩn hóa. Email activation/reset encode từng path segment, nên email/token có ký tự URL-reserved vẫn tạo liên kết hợp lệ.

## Render Blueprint

`render.yaml` mô tả một Docker web service trên gói Render Free, dùng `GET /health` làm health check và kết nối Aiven Free MySQL. Khi tạo Blueprint, nhập `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` bằng form bảo mật của Render; dùng JDBC URL dạng `jdbc:mysql://<host>:<port>/defaultdb?sslmode=require`. Blueprint tự sinh `JWT_SECRET` và cấu hình origin Vercel công khai. Render Free không hỗ trợ persistent disk/private service và chặn SMTP ports, nên mail chưa hoạt động; VNPay/Cloudinary chỉ bật sau khi thêm credentials. Không commit các giá trị Aiven vào repository.

`FlywayConfig` chạy `flyway.repair()` trước mỗi `flyway.migrate()` để tự đồng bộ `flyway_schema_history` khi một lần deploy trước đó fail giữa chừng (ví dụ do lỗi cấu hình DB_URL), không cần truy cập SQL thủ công. `repair()` chỉ sửa metadata lịch sử (checksum, xóa dòng `failed`) khớp với các file migration hiện có trên classpath; nó không rollback hay sửa dữ liệu/schema đã áp dụng. Vì vậy không được sửa nội dung một migration đã từng chạy production — luôn thêm migration mới.

## Tồn Kho

### Contract quản trị

| Method | Path | Quyền | Hành vi |
|---|---|---|---|
| `POST` | `/api/admin/sach/insert` | ADMIN | Nhận và ghi `soLuongTon` ban đầu khi là số nguyên `>= 0`. |
| `PUT` | `/api/admin/sach/update/{id}` | ADMIN | Chỉ cập nhật metadata; trường legacy `soLuongTon` bị bỏ qua, kể cả stale, `null`, hoặc âm. |
| `PATCH` | `/api/admin/sach/{id}/ton-kho` | ADMIN | Điều chỉnh tồn kho bằng delta có dấu, nguyên tử. |

`PATCH` nhận đúng payload sau:

```json
{"soLuongThayDoi": 5}
```

`soLuongThayDoi` phải là số nguyên Java khác `0`. Thành công trả về giá trị kho authoritative từ máy chủ:

```json
{"maSach": 123, "soLuongTon": 13}
```

| Trường hợp | HTTP status |
|---|---:|
| Body thiếu, `null`, `0`, không phải số nguyên, hoặc JSON sai | 400 |
| Sách không tồn tại | 404 |
| Delta làm tồn âm hoặc vượt `Integer.MAX_VALUE` | 409 |
| Không đăng nhập hoặc không phải ADMIN | 401/403 |

Checkout trừ kho và hủy đơn hoàn kho bằng các `UPDATE` có điều kiện trong transaction. Checkout, cancellation và admin delta đều giữ tồn trong miền `0..Integer.MAX_VALUE`; không dùng read-then-write hoặc arithmetic có thể tràn. `Sach` dùng `@DynamicUpdate`, vì vậy một `PUT` metadata chỉ flush cột metadata dirty và không ghi đè kho vừa thay đổi. `SachUserController` chỉ khai báo các route đọc dưới `/api/sach`, nên không có raw controller write tại đây để bypass contract. Spring Data REST tắt `POST`, `PUT`, `PATCH`, `DELETE` cho `Sach` ở collection, item và association; GET quan hệ `/sach/{id}/listDanhGia` vẫn được giữ.

Frontend quản trị gửi payload create/update riêng: create có `soLuongTon`, metadata update không có. Số tồn trên trang sửa là read-only; thao tác delta riêng chỉ dùng `soLuongTon` trong response thành công, không optimistic arithmetic. Sau một lỗi mạng không thể xác định kết quả, người dùng phải tải lại tồn trước khi thử lại.

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
- `PATCH /api/admin/sach/{id}/ton-kho`
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

## Xác Minh Inventory Delta

### Cổng tĩnh và build

`*IT` integration classes are bound to Maven Failsafe and run during `mvn verify`; `test-compile` only verifies compilation.

```bash
# Backend compile gate
docker run --rm -v "${PWD}:/app" -w /app maven:3.9-eclipse-temurin-17   mvn -B clean test-compile

# Run Failsafe integration tests when Docker/Testcontainers is available
./mvnw -B verify

# Docker Engine 29 + Testcontainers 1.19.8: run Maven in Docker with
# the mounted socket and a process-local compatible API version
MSYS_NO_PATHCONV=1 docker run --rm \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  -v "${PWD}:/app" -v /var/run/docker.sock:/var/run/docker.sock \
  -w /app maven:3.9-eclipse-temurin-17 \
  mvn -B clean verify -Dapi.version=1.44

# Frontend production build and TypeScript check (repo ../book_FE)
cd ../book_FE
npm run build
npx tsc --noEmit
```

### Ranh giới bằng chứng

Ngày 2026-07-14, full `mvn -B clean verify` trong Maven-in-Docker với socket Docker Desktop, `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal` và process-local `-Dapi.version=1.44` đã chạy xanh Surefire **14/14** (gồm strict DTO 3/3) và bốn lớp Failsafe **28/28**. Các assertion Testcontainers/MySQL thật gồm stale metadata, lower/upper bound, checkout/cancel/admin concurrency, HTTP status/auth/CORS và Data REST write closure. HTTP stock-delta smoke cũng đạt **45/45 hai lần**; mỗi lần cleanup exact-ID thành công, mandatory flow kết thúc ở 13, checkout/admin contention kết thúc ở 13 và cancel/admin contention kết thúc ở 9. Contention HTTP không thay thế deterministic concurrency tests; bằng chứng deterministic đến từ các `*IT` dùng latch/future timeout.

### Điều kiện chạy smoke runtime

The runtime smoke needs a running Docker Compose stack, a valid externally supplied `JWT_SECRET`, and local smoke credentials prepared by `scripts/smoke-setup.sh`.

```bash
cd ../book_BE
docker compose up --build -d
./scripts/smoke-setup.sh
./scripts/kiem-tra-ton-kho-delta.sh
./scripts/kiem-tra-ton-kho-delta.sh
```

`scripts/kiem-tra-ton-kho-delta.sh` creates a fixture with a unique run ID, captures exact IDs, installs cleanup traps before mutation, and removes only its own fixture. The two verified runs on 2026-07-14 each passed 45/45 and exact-ID cleanup. It is an HTTP/contention smoke, not a substitute for the deterministic Testcontainers concurrency tests.

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
