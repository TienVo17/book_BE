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

### Quy Tắc Nghiệp Vụ Giỏ Hàng

- **Nguồn sự thật:** `/api/gio-hang/**` là giỏ server theo user đã xác thực; `GioHangRepository` phải giữ `@RepositoryRestResource(exported = false)`. Không tạo route public hoặc đường Data REST để đọc/ghi giỏ.
- **Serialization ghi theo user:** mọi mutation giỏ (`addItem`, `updateItemQuantity`, `removeItem`, `mergeGuestCart`, `clearCurrentUserCart`) và checkout phải lấy user bằng `NguoiDungRepository.findByTenDangNhapForCartWrite`. Không đổi thành read-then-write, lock chỉ giỏ, hoặc bỏ khóa: unique constraint không tự ngăn lost update trên một dòng đã tồn tại.
- **Ràng buộc dữ liệu:** `gio_hang` phải có tối đa một dòng cho `(ma_nguoi_dung, ma_sach)` và `so_luong > 0`. Không lưu dòng quantity `0`; `PUT` quantity `0` phải xóa. Khi có migration sửa dữ liệu legacy, dọn/gộp/cap dữ liệu trước rồi mới thêm constraint.
- **Sách khả dụng:** thêm/cập nhật trực tiếp từ chối sách không tồn tại, inactive, hết hàng hoặc lượng vượt tồn. Merge bỏ sách không tồn tại/inactive/hết hàng vào `removedItems`; cap lượng theo tồn và báo `CAPPED_TO_STOCK` trong `adjustedItems`. Summary không trả dòng đã inactive hoặc hết hàng.
- **Merge idempotent:** `POST /api/gio-hang/merge` bắt buộc `Idempotency-Key` đã trim, dài 1–100, regex `^[A-Za-z0-9._-]+$`. Chuẩn hóa/gộp payload trước khi băm SHA-256. Receipt phải lưu fingerprint và toàn bộ response snapshot; fingerprint trùng replay snapshot không mutation, khác trả `409` trước mutation. Bảng receipt giữ unique `(ma_nguoi_dung, idempotency_key)` và FK user `ON DELETE CASCADE`.
- **Dọn sau checkout:** chỉ xóa các dòng giỏ có sách đã thực sự tạo trong `ChiTietDonHang`, trong cùng transaction checkout. Không gọi clear-cart chung; replay checkout không được có mutation thứ hai.

### Quy Tắc Nghiệp Vụ Đánh Giá

- **Không thêm cột đếm sẵn cho lượt hữu ích.** Khác `trung_binh_xep_hang` (đã có trong contract, thiếu writer là bug thật), một cột đếm ở đây là denormalize hoàn toàn mới — thêm một nguồn sự thật thứ hai phải canh, cho một cửa hàng mười cuốn sách. Đếm bằng một câu `GROUP BY` trên tập id của trang.
- **Chặn tự bình chọn ở service.** Ẩn nút chỉ ngăn người dùng bình thường, không ngăn một request gửi thẳng tới API. Tính duy nhất do ràng buộc `uk_danhgia_huu_ich_nguoi` bảo đảm, không phải do kiểm tra ở service.
- **Che tên chạy ở backend** (`shared/util/TenHienThiUtil`). Che ở frontend thì tên đầy đủ vẫn nằm nguyên trong response — đó là trang trí, không phải bảo vệ.
- **Đường công khai không trả `maNguoiDung`.** Từ khi chỉ người đã nhận hàng mới đánh giá được, mỗi đánh giá là bằng chứng của một đơn đã giao; một định danh ổn định đi kèm cho phép quét cả catalog rồi dựng lại lịch sử mua hàng từng người. Quyền sở hữu do cờ `laCuaToi` mang.
- **Hai DTO, hai khán giả.** `DanhGiaCongKhaiResponse` cho `/api/danh-gia**`: không `maNguoiDung`, không `isActive`. `DanhGiaQuanTriResponse` cho `/api/admin/danh-gia**`: có danh tính thật. Đừng gộp lại — test PII của đường công khai sẽ khoá chết khả năng nhìn thấy tên thật của màn kiểm duyệt, mà đó chính là việc của admin.
- **Phân bố sao và `tongSo` luôn tính trên toàn bộ đánh giá `HIEN_THI`**, không theo bộ lọc đang chọn. Tính theo bộ lọc thì bấm vào một cột sẽ làm các cột còn lại về 0.
- **Mọi kiểu sắp xếp phải có khoá phụ `maDanhGia`.** Không có nó, hai đánh giá cùng timestamp làm thứ tự không ổn định và một dòng có thể xuất hiện ở hai trang liên tiếp hoặc không ở trang nào.
- **Đường ghi của admin phải nạp kèm `nguoiDung`** (`SuDanhGiaRepository.timKemNguoiDung`). `SuDanhGia.nguoiDung` là LAZY và entity rời transaction trước khi controller dựng response — `findById` thường sẽ sinh 500 ngay ở thao tác ẩn/hiện.
- **Chỉ người đã nhận hàng mới đánh giá được.** Điều kiện đủ: tồn tại `don_hang` của chính người gửi, có `chi_tiet_don_hang` chứa cuốn sách đó, `trang_thai_giao_hang = 2 (DA_GIAO)`. `danhgia.ma_don_hang` phải trỏ đúng đơn đó. `GET /api/danh-gia/co-the-danh-gia` chỉ là tiện ích hiển thị — `DanhGiaServiceImpl.addReview` **luôn** kiểm tra lại, không tin giá trị client gửi lên.
- **`chi_tiet_don_hang.danh_gia` là cờ chết.** Có từ `V1__init_schema.sql` nhưng chưa service nào ghi, và cố tình không dùng lại: nó ở mức dòng đơn hàng chứ không phải mức `(người, sách)`, mà một người mua lại cùng cuốn sách nhiều lần là chuyện bình thường. Đừng suy ra ý nghĩa nào từ giá trị của nó.
- **Ẩn đánh giá phải ghi tombstone.** `danhgia_an_tombstone` giữ cặp `(ma_nguoi_dung, ma_sach)` đã bị ẩn và **không** cascade theo `danhgia` — đó là toàn bộ lý do nó tồn tại. `danhgia.tung_bi_an` chết cùng dòng khi chủ sở hữu tự xoá, nên không dùng nó làm căn cứ chặn đăng lại được.
- **Sửa nội dung không được đổi `trangThai`.** Nếu không, "sửa" trở thành cách tự bỏ ẩn rẻ hơn cả xoá-rồi-đăng-lại.
- **Đơn demo không được vào thống kê.** Đơn do `V12` sinh ra mang `don_hang.la_don_demo = 1`; mọi truy vấn thống kê trong `DonHangRepository` và `ChiTietDonHangRepository.findTopBanChay` phải loại chúng. Loại ở vài truy vấn thôi thì còn tệ hơn — dashboard sẽ tự mâu thuẫn.
- **Chủ sở hữu đơn demo:** chính các tài khoản sở hữu đánh giá seed của `V4` (người dùng 2–6). `V10` đã đặt `da_kich_hoat = 0` cho người dùng 1–5, nên **không đăng nhập được bằng các tài khoản này**. Mọi bước kiểm chứng thủ công phải tạo tài khoản mới; đơn demo chỉ tồn tại để đánh giá cũ có bằng chứng đã mua, không phải để thao tác qua giao diện.
- **Spring Data REST:** write methods của `Sach` phải bị tắt tại collection, item và association. `SuDanhGiaRepository` là `@RepositoryRestResource(exported = false)` — `/su-danh-gia/**` và relation `/sach/{id}/listDanhGia` đều trả 404 và **không được mở lại**: relation này bỏ qua mọi bộ lọc trạng thái nên đánh giá bị admin ẩn vẫn đọc được công khai. Đường đọc đánh giá công khai duy nhất là `GET /api/danh-gia?maSach=`, đã lọc `trangThai = HIEN_THI`. Relation `/sach/{id}/listTheLoai` vẫn mở; `/sach/{id}/listHinhAnh` đã đóng từ trước vì `HinhAnhRepository` là `exported = false`.

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
- Rate limiting qua `RateLimiter` (in-memory, per-process) cho đăng nhập, đăng ký, kích hoạt, quên/đặt lại mật khẩu. Đăng nhập **chỉ đếm lần sai** và reset khi thành công, nên người dùng hợp lệ không bị khóa. Trần theo IP đặt cao để không chặn nhầm mạng NAT dùng chung
- Thông báo đăng nhập thất bại giống nhau cho mọi nguyên nhân, không tiết lộ tài khoản tồn tại
- Endpoint phân quyền trong `SecurityConfiguration`, kết thúc bằng `anyRequest().denyAll()` — route mới phải được khai báo tường minh
- Quyền sở hữu tài nguyên (đơn hàng, địa chỉ, đánh giá) kiểm tra ở service, không chỉ dựa vào matcher đường dẫn

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
│   │       └── db/migration/           # Flyway migrations (V1–V19) + beforeMigrate.sql callback
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
- Test giỏ server phải cover auth controller, quantity/ID/key invalid, sách inactive/hết hàng, cap tồn, unique/check migration, replay/conflict merge, response snapshot, và hai mutation đồng thời cùng user. `CartMergeIT` cùng Testcontainers là bằng chứng deterministic cho serialization; khi Docker Engine 29 không chạy được Testcontainers 1.19.8 trên máy host, dùng Maven-in-Docker với socket và `-Dapi.version=1.44` như README thay vì coi smoke runtime là thay thế.

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
