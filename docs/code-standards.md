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

### Mô Hình Phân Lớp

```
Controller (REST API)
    │
    ▼
Service (Business Logic)
    │
    ▼
DAO / Repository (Data Access - Spring Data JPA)
    │
    ▼
Entity (JPA Entities - MySQL)
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
book_BE-main/
├── src/
│   ├── main/
│   │   ├── java/com/example/book_be/   # Mã nguồn Java
│   │   └── resources/
│   │       └── application.properties  # Cấu hình
│   └── test/                           # Unit tests
├── db/init/                            # SQL khởi tạo DB
├── docs/                               # Tài liệu dự án
├── plans/                              # Kế hoạch phát triển
├── pom.xml                             # Maven config
├── Dockerfile                          # Docker build
├── docker-compose.yml                  # Docker compose
└── web_ban_sach.sql                    # Full DB dump
```
