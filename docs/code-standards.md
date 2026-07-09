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
- Cấu hình global trong `SecurityConfiguration` (cho API)
- Cấu hình riêng trong `RestConfig` (cho Spring Data REST endpoints)
- Allowed origin: `http://localhost:3000`

### Bảo Mật
- JWT stateless, token hết hạn 30 phút
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
│   │       └── db/migration/           # Flyway migrations (V1–V6)
│   └── test/                           # Characterization tests
├── scripts/                            # smoke.sh, smoke-setup.sh (lưới an toàn HTTP)
├── docs/                               # Tài liệu dự án
├── plans/                              # Kế hoạch phát triển
├── pom.xml                             # Maven config
├── Dockerfile                          # Docker build
└── docker-compose.yml                  # Docker compose (mysql, backend, frontend)
```

## Những Vấn Đề Đã Biết (Known Issues)

### Bảo Mật (Security)
- Authorization dùng string-array matching (`Endpoints`) first-match-wins. **Lưu ý bất biến:** phân quyền
  phụ thuộc REST path — không đổi `@RequestMapping` path khi refactor package.
- Hai cấu hình CORS: `SecurityConfiguration` giới hạn origin `http://localhost:3000`, nhưng `RestConfig`
  (Spring Data REST) cho phép mọi origin trên `/**` — cân nhắc siết lại.

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
- Hardcoded fallback defaults cho `jwt.secret` và VNPay credentials khi env vars không được set.
