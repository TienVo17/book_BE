# Tiêu Chuẩn Mã Nguồn

## Quy Ước Đặt Tên

### Ngôn Ngữ
- **Entity, field, controller path**: Tiếng Việt (viết không dấu hoặc camelCase tiếng Việt)
- **Package, class, method Java**: Quy ước Java chuẩn
- **Database column**: snake_case tiếng Việt không dấu

### Ví Dụ Đặt Tên

| Loại | Ví dụ | Ghi chú |
|------|-------|---------|
| Entity | `Sach`, `NguoiDung`, `DonHang` | PascalCase tiếng Việt |
| Field | `tenSach`, `giaBan`, `maNguoiDung` | camelCase tiếng Việt |
| DB column | `ten_sach`, `gia_ban`, `ma_nguoi_dung` | snake_case |
| Controller path | `/tai-khoan/dang-ky`, `/api/don-hang/them` | kebab-case tiếng Việt |
| Repository | `SachRepository`, `NguoiDungRepository` | Entity + Repository |
| Service | `SachService`, `SachServiceImpl` | Interface + Impl |
| BO/DTO | `SachBo`, `UserBo` | Entity + Bo |

### Tiền Tố Phổ Biến

| Tiền tố | Ý nghĩa | Ví dụ |
|---------|---------|-------|
| `ma` | Mã (ID) | `maSach`, `maNguoiDung` |
| `ten` | Tên | `tenSach`, `tenDangNhap` |
| `dia_chi` | Địa chỉ | `diaChiMuaHang` |
| `trang_thai` | Trạng thái | `trangThaiThanhToan` |
| `danh_sach` | Danh sách | `danhSachQuyen` |
| `is_` | Boolean flag | `isActive`, `isAdmin` |
| `so` | Số | `soLuong`, `soDienThoai` |

## Kiến Trúc

Dự án dùng **package-by-feature hybrid**: mỗi nghiệp vụ (`sach`, `nguoidung`, `giohang`, `donhang`,
`thanhtoan`, `danhgia`, `giamgia`, `seo`, `thongke`, `yeuthich`) là một package sở hữu đủ các tầng con
`web/ service/ repository/ domain/ dto/` của riêng nó; cross-cutting nằm ở `shared/`. Không còn package
tầng dùng chung (`controller/ services/ dao/ entity/ bo/`). Chi tiết: `docs/architecture-review.md`.

### Mô Hình Phân Lớp (bên trong mỗi feature)

```
web/ (REST Controller)
    │
    ▼
service/ (Business Logic — interface + Impl)
    │
    ▼
repository/ (Data Access — Spring Data JPA)
    │
    ▼
domain/ (JPA Entities - MySQL)
```

### Quy Tắc Từng Lớp

**Controller**
- Annotate `@RestController`, `@RequestMapping`
- Inject service qua `@Autowired`
- Return `ResponseEntity<?>` hoặc entity trực tiếp
- Không chứa business logic phức tạp

**Service**
- Interface + Implementation (`@Service`)
- Chứa business logic, validation
- Gọi repository để truy vấn DB

**Repository (DAO)**
- Extends `JpaRepository` hoặc `JpaSpecificationExecutor`
- Custom query qua `@Query` hoặc method naming convention
- Sử dụng `Specification` cho dynamic queries

**Entity**
- `@Entity`, `@Table`, `@Data` (Lombok)
- `@Id`, `@GeneratedValue(strategy = GenerationType.IDENTITY)`
- Quan hệ: `@ManyToOne`, `@OneToMany`, `@ManyToMany`
- `@JsonIgnore` cho lazy-loaded collections tránh circular reference

### Quy Tắc Nghiệp Vụ Đơn Hàng (Cụm A)

- **Trạng thái đơn hàng CHỈ được thay đổi qua `DonHangTrangThaiService`** (`chuyenTrangThaiGiaoHang`/`chuyenTrangThaiTiepTheo`/`chuyenTrangThaiThanhToan`) — không `setTrangThai*` trực tiếp trong controller/service khác (ngoại lệ: checkout khởi tạo 0/0). Mọi chuyển hợp lệ tự ghi `lich_su_trang_thai_don_hang`.
- **Trừ/hoàn/điều chỉnh tồn kho CHỈ qua query nguyên tử** `SachRepository.truKhoNeuDu`, `hoanKho`, `tangTonKhoNeuKhongVuotQua`, hoặc `giamTonKhoNeuDu` (UPDATE có điều kiện, caller `@Transactional`) — không đọc-rồi-ghi `soLuong`. Lặp theo `maSach` tăng dần (TreeMap/sorted) để chống deadlock.
- **Tác dụng phụ ngoài transaction** (email...) đăng ký qua `TransactionSynchronization.afterCommit`, không nằm trong `@Transactional` — tránh self-invocation làm mất transaction và không để SMTP lỗi rollback nghiệp vụ đã commit.
- Entity cần chống lost-update (vd `DonHang`) dùng `@Version` + `@JsonIgnore`; bắt `ObjectOptimisticLockingFailureException` → 409.
- **Tồn kho:** `Sach.soLuong` luôn trong `0..Integer.MAX_VALUE`. Checkout chỉ nhận quantity dương, aggregate duplicate line bằng `long` và từ chối aggregate ngoài `int`; cancellation restore và admin delta phải dùng predicate upper/lower bound trong chính câu `UPDATE`. Không thực hiện arithmetic có thể overflow trong `WHERE`.
- **Tách intent tạo/sửa:** `POST /api/admin/sach/insert` validate/ghi `soLuongTon >= 0`; `PUT /api/admin/sach/update/{id}` không được gán, validate, hoặc khôi phục `soLuongTon` từ payload legacy, kể cả `null`, âm hoặc stale. `Sach` dùng `@DynamicUpdate` để metadata flush chỉ ghi dirty columns.
- **Điều chỉnh runtime:** chỉ `PATCH /api/admin/sach/{id}/ton-kho` được dùng cho signed delta; request là `{soLuongThayDoi: integer khác 0}`, response scalar `{maSach,soLuongTon}` phải được coi là authoritative. Dùng exception/status 400 (invalid), 404 (missing), 409 (range conflict).
- **Spring Data REST:** write methods của `Sach` phải bị tắt tại collection, item và association. Giữ và regression-test GET relation `/sach/{id}/listDanhGia` trước khi đổi export/read contract.

## Phong Cách Code

### Annotations
- Sử dụng `@Autowired` (field injection) — pattern hiện tại của project
- Lombok `@Data` cho getter/setter/toString

### Exception Handling
- Try-catch trong controller, `e.printStackTrace()` cho debug
- Return `ResponseEntity.badRequest().body(message)` cho lỗi

### Phân Trang
- Sử dụng Spring Data `Pageable` và `Page<T>`
- Default page size: 8 (user) hoặc 10 (admin)

### CORS
- Cấu hình tập trung duy nhất trong `SecurityConfiguration`, áp dụng cho API và Spring Data REST.
- Origin chính xác lấy từ `app.frontend-url` / `FRONTEND_URL`, chuẩn hóa slash cuối; không dùng wildcard, controller-local annotation, hoặc CORS registration trong `RestConfig`.
- Giữ methods `GET, POST, PUT, PATCH, DELETE, OPTIONS`, headers `*`, credentials và max age 3600 giây.

### Bảo Mật
- JWT stateless; `JWT_SECRET` Base64 phải do môi trường runtime cấp (không fallback hoặc tự sinh khóa); expiration cấu hình qua `JWT_EXPIRATION_MS` (mặc định 8 giờ / `28800000` ms)
- BCrypt cho mật khẩu
- Rate limiting đăng nhập (in-memory ConcurrentHashMap)
- Endpoint phân quyền trong `SecurityConfiguration`

## Cấu Trúc Thư Mục Dự Án

```
book_BE/
├── src/
│   ├── main/
│   │   ├── java/com/example/book_be/   # Mã nguồn Java (package-by-feature)
│   │   │   ├── sach/  nguoidung/  yeuthich/  giohang/  donhang/
│   │   │   ├── thanhtoan/  danhgia/  giamgia/  seo/  thongke/
│   │   │   └── shared/                 # config, security, util, dto, email
│   │   └── resources/
│   │       ├── application.properties  # Cấu hình
│   │       └── db/migration/           # Flyway migrations (V1–V8) + beforeMigrate.sql callback
│   └── test/                           # Unit tests và Testcontainers integration tests
├── scripts/                            # smoke scripts, gồm kiem-tra-ton-kho-delta.sh
├── docs/                               # Tài liệu dự án
├── plans/                              # Kế hoạch phát triển
├── pom.xml                             # Maven config
├── Dockerfile                          # Docker build
└── docker-compose.yml                  # Docker compose (mysql, backend, frontend)
```

## Quy Ước Kiểm Thử

- Unit test dùng tên `*Test` và chạy trong Surefire.
- Integration test cần MySQL thật/Testcontainers dùng hậu tố `*IT` (ví dụ `SachTonKhoIT`, `SachAdminTonKhoControllerIT`). `maven-failsafe-plugin` chạy chúng tại `mvn verify`, không phải `mvn test`.
- `mvn -B clean test-compile` chỉ xác minh main/test sources compile. Không ghi đó là kết quả pass của test behavior hay concurrency.
- Test tồn kho phải cover writer bounds, stale metadata, HTTP 400/404/409/auth, Data REST write closure/relation GET, và concurrency với latch, timeout, futures/error propagation. Shell HTTP smoke chỉ bổ sung; không thay thế deterministic integration test.

## Những Vấn Đề Đã Biết (Known Issues)

### Bảo Mật (Security)
- Authorization dùng string-array matching (`Endpoints`) first-match-wins. **Lưu ý bất biến:** phân quyền
  phụ thuộc REST path — không đổi `@RequestMapping` path khi refactor package.

### Bảo Trì (Maintenance)
- Một số method service trả về `null` như stubs: `AdminUserServiceImpl.save/update/delete/findById`, `SachServiceImpl.delete`.
- `DonHangAdminController.findAll` lọc theo đơn hàng của admin yêu cầu thay vì trả tất cả đơn.

### Đã Khắc Phục
- Public admin GET endpoints (`/api/admin/user**`, `/api/admin/sach**`) và mutation không phân quyền ở
  `SachUserController`: siết lại ở nhánh `security-hardening` (PR #1).
- Lộ hash mật khẩu qua JPA entity: `NguoiDung` ẩn field nhạy cảm, API công khai trả DTO.
- `TaiKhoanService`: xóa method kích hoạt trùng (`kichHoatTaiKHoan`) và `main()` thử bcrypt; `VnPayConfig`: xóa `md5`/`Sha256` không dùng.

### Validation
- `BookDescriptionSanitizer` sử dụng regex-based sanitization thay vì thư viện HTML parser chuyên dụng.
