# Tóm Tắt Mã Nguồn

## Thống Kê

- **Số file Java**: không ghi số cố định; số lượng thay đổi cùng mã nguồn và test.
- **Java version**: 17
- **Package gốc**: `com.example.book_be`
- **Kiến trúc**: **package-by-feature hybrid** — mỗi nghiệp vụ là một package sở hữu đủ các tầng con
  `web/ service/ repository/ domain/ dto/` của riêng nó. (Xem `docs/architecture-review.md`.)

## Cấu Trúc Package

Mỗi feature package chứa các tầng con: `web/` (controller), `service/`, `repository/`, `domain/` (entity), `dto/`.

```
src/main/java/com/example/book_be/
├── BookBeApplication.java              # Entry point
├── sach/                               # Catalog: sách, thể loại, hình ảnh, nhà cung cấp
│   ├── web/          SachUserController, TheLoaiController, SachController(admin), AdminTheLoaiController
│   ├── service/      SachService(+Impl), SachNotFoundException, StockAdjustmentConflictException, TheLoaiService(+Impl), CloudinaryService, BookImageStorageService
│   ├── repository/   SachRepository, TheLoaiRepository, HinhAnhRepository, NhaCungCapRepository
│   ├── domain/       Sach, TheLoai, HinhAnh, NhaCungCap, SachThongTinChiTiet
│   └── dto/          SachResponse, SachBo, SachAdminUpsertBo, SachThongTinChiTietBo, SachTonKhoDieuChinhRequest, SachTonKhoResponse, TheLoai{Response,AdminResponse,AdminUpsertRequest}
├── nguoidung/                          # Người dùng + xác thực
│   ├── web/          TaiKhoanController, NguoiDungController, DiaChiController, UserController(admin), QuyenController
│   ├── service/      TaiKhoanService, NguoiDungService(+Impl), UserService(+Impl), DiaChiService(+Impl), AdminUserService(+Impl)
│   ├── repository/   NguoiDungRepository, QuyenRepository, DiaChiGiaoHangRepository
│   ├── domain/       NguoiDung, Quyen, DiaChiGiaoHang
│   ├── dto/          UserBo, PhanQuyenBo
│   └── baomat/       JwtService, Jwtfilter          # JWT (đổi từ package `services/JWT` chữ HOA)
├── yeuthich/         web/ · repository/ · domain/(SachYeuThich)
├── giohang/          web/ · service/(CartService+Impl) · repository/ · domain/(GioHang) · dto/(Cart*)
├── donhang/          web/(DonHangController, DonHangAdminController) · service/(OrderService+Impl, DonHangTrangThaiService, DonHangHuyService) · repository/(DonHangRepository, ChiTietDonHangRepository, LichSuTrangThaiDonHangRepository) · domain/(DonHang, ChiTietDonHang, HinhThucGiaoHang, LichSuTrangThaiDonHang, MayTrangThaiDonHang, TrangThaiGiaoHang, TrangThaiThanhToan) · dto/(CheckoutOrder{Request,Response}, OrderListItemResponse, VNPayUrlResponse)
├── thanhtoan/        config/(VnPayConfig) · service/(VNPayService) · repository/ · domain/(HinhThucThanhToan)
├── danhgia/          web/(DanhGiaController, BinhLuanController) · service/(DanhGiaService+Impl) · repository/(SuDanhGiaRepository) · domain/(SuDanhGia) · dto/(DanhGiaBo)
├── giamgia/          web/(CouponController, CouponAdminController) · service/(CouponService+Impl) · repository/(CouponRepository) · domain/(Coupon, LoaiGiamGia)
├── seo/              web/(SeoController, SitemapController) · service/(SeoService+Impl)
├── thongke/          web/(admin/ThongKeController) · service/(ThongKeService+Impl)
└── shared/                             # Cross-cutting
    ├── config/       RestConfig, CloudinaryConfig
    ├── security/     SecurityConfiguration, Endpoints, LoginRequest, JwtResponse
    ├── util/         SlugUtil, BookDescriptionSanitizer
    ├── dto/          ThongBao, BaseBo
    └── email/        EmailService(+Impl)
```

> Lưu ý: `VnPayConfig` thuộc `thanhtoan/config/` (nghiệp vụ thanh toán), KHÔNG phải `shared/config/`.
> `CouponRepository.tangLuotSuDungNeuConHieuLuc(...)` cập nhật nguyên tử lượt dùng coupon khi checkout.

## Quan Hệ Thực Thể (Entity Relationships)

```
                    ┌─────────────┐
                    │  TheLoai    │
                    │ (Thể loại) │
                    └──────┬──────┘
                           │ M:N (sach_theloai)
                           │
┌──────────┐        ┌──────┴──────┐        ┌──────────────┐
│NhaCungCap├───N:1──┤    Sach     ├──1:N──►│   HinhAnh    │
│(Nhà CC)  │        │   (Sách)   │        │ (Hình ảnh)   │
└──────────┘        └──┬──┬──┬───┘        └──────────────┘
                       │  │  │
              1:N──────┘  │  └──────1:N
              │           │           │
     ┌────────┴───┐  ┌────┴────┐  ┌──┴──────────┐
     │ SuDanhGia  │  │GioHang  │  │SachYeuThich │
     │ (Đánh giá) │  │(Giỏ)   │  │(Yêu thích)  │
     └─────┬──────┘  └────┬───┘  └──────┬──────┘
           │ N:1          │ N:1         │ N:1
           │              │             │
           └──────────────┼─────────────┘
                          │
                   ┌──────┴──────┐
                   │  NguoiDung  │
                   │(Người dùng) │
                   └──────┬──────┘
                          │ M:N (nguoidung_quyen)
                   ┌──────┴──────┐
                   │   Quyen     │
                   │  (Quyền)    │
                   └─────────────┘

NguoiDung ──1:N──► DonHang ──1:N──► ChiTietDonHang ──N:1──► Sach
                      │
                      ├──N:1──► HinhThucThanhToan
                      └──N:1──► HinhThucGiaoHang
```

## File Cấu Hình

| File | Mô tả |
|------|-------|
| `pom.xml` | Maven dependencies (Spring Boot, JPA, Flyway, Security, JWT, Cloudinary, Lombok) |
| `application.properties` | DB URL, JWT secret, SMTP config, Flyway config (`ddl-auto=validate`) |
| Docker image config (root) | Multi-stage build: Maven → JRE 17 |
| `docker-compose.yml` | 3 services: MySQL, Backend, Frontend (frontend build context: `../book_FE`) |
| `src/main/resources/db/migration/` | Flyway migrations (V1-V7): schema, seed, demo data, slug thể loại, payment codes, lịch sử trạng thái + `ma_coupon`/`version` đơn hàng |
| `repomix-output.xml` | Compaction snapshot dùng để tổng hợp codebase/docs sync |
| `.gitignore` | Loại trừ target, IDE files |

## Database Migration (Flyway)

| File | Mô tả |
|------|-------|
| `V1__init_schema.sql` | Baseline schema: 17 bảng, PKs, FKs, indexes |
| `V2__seed_reference_data.sql` | Quyền, hình thức GH/TT |
| `V3__seed_default_admin.sql` | Tài khoản admin mặc định |
| `V4__seed_demo_data.sql` | Demo: 10 sách, 5 users, đơn hàng, đánh giá |
| `V5__add_slug_to_the_loai.sql` | Thêm `slug`, backfill dữ liệu cũ, và unique constraint cho `the_loai` |
| `V6__add_payment_method_codes.sql` | Thêm cột `ma_code` cho `hinh_thuc_thanh_toan`, backfill COD/VNPAY |
| `V7__lich_su_trang_thai_va_ma_coupon.sql` | Bảng `lich_su_trang_thai_don_hang`; `don_hang.ma_coupon` (FK `ON DELETE SET NULL`); `don_hang.version` (`@Version`) |

Schema quản lý bởi Flyway, Hibernate chỉ `validate`. Mọi thay đổi schema phải qua migration mới (V5, V6, V7...).

## Tồn Kho Delta

`Sach` có `@DynamicUpdate`; metadata update trên entity managed chỉ flush các cột dirty. Đây là lớp bảo vệ bổ sung cho rule service: `POST /api/admin/sach/insert` ghi `soLuongTon` ban đầu khi `>= 0`, nhưng `PUT /api/admin/sach/update/{id}` bỏ qua `soLuongTon` legacy, gồm stale, `null`, và âm.

| Thành phần | Vị trí | Trách nhiệm |
|---|---|---|
| Request DTO | `sach/dto/SachTonKhoDieuChinhRequest` | Chỉ chấp nhận `soLuongThayDoi` integral trong miền `int`; service từ chối `null`/`0`. |
| Response DTO | `sach/dto/SachTonKhoResponse` | Trả scalar authoritative `{maSach, soLuongTon}` sau adjustment. |
| Exceptions | `sach/service/SachNotFoundException`, `StockAdjustmentConflictException` | Ánh xạ thiếu sách thành 404 và vi phạm bound thành 409 tại `SachController`. |
| Service | `SachServiceImpl.dieuChinhTonKho` | Tách positive/negative delta, dùng bounds atomic, rồi đọc lại scalar stock. |
| Repository | `SachRepository` | Conditional decrement, bounded restore, bounded positive delta và scalar lookup. |
| Web | `SachController` | `PATCH /api/admin/sach/{id}/ton-kho`; malformed JSON, invalid body, 404 và 409 trả error body. |

Các writer checkout/cancel/admin đều giữ `0 <= soLuong <= Integer.MAX_VALUE`. Checkout gộp duplicate line bằng `long`, từ chối aggregate vượt `int`, rồi trừ bằng query điều kiện. Cancel chỉ hoàn nếu upper bound còn đủ; lỗi hoàn rollback transaction. Vì vậy không có lower/upper `int` overflow hay mint stock bằng số lượng không dương.

Frontend ở repo sibling `../../book_FE` (tính từ file tài liệu này) phân tách `toSachAdminCreatePayload` (có `soLuongTon`) và `toSachAdminUpdatePayload` (không có field này). `CapNhatSach` hiển thị tồn read-only, gửi delta qua action riêng, thay UI từ `SachTonKhoResponse`, và khóa action cho tới khi reload khi lỗi mạng không phân loại được.

## Spring Data REST cho `Sach`

`RestConfig` tắt `POST`, `PUT`, `PATCH`, `DELETE` cho `Sach` tại collection, item và association. GET repository/relationship vẫn còn, đặc biệt `/sach/{id}/listDanhGia`; raw repository writes không được coi là một con đường hợp lệ để cập nhật stock.

## Kiểm Thử Inventory

`SachTonKhoDieuChinhRequestTest` là unit test DTO. `SachTonKhoIT` và `SachAdminTonKhoControllerIT` là Testcontainers integration classes (`*IT`) với concurrency harness dùng `CountDownLatch`, futures, timeout và failure propagation. `maven-failsafe-plugin` chạy các lớp này ở `mvn verify`; `mvn clean test-compile` chỉ compile mã test, không thực thi chúng.

Ngày 2026-07-14, full `mvn -B clean verify` trong Maven-in-Docker gắn Docker Desktop socket và dùng process-local `-Dapi.version=1.44` đã chạy Surefire 14/14 (gồm strict DTO 3/3) và bốn lớp Failsafe 28/28 trên MySQL Testcontainers. `SachTonKhoIT` thực thi các race deterministic bằng latch/future timeout; `SachAdminTonKhoControllerIT` xác minh HTTP contract/auth/CORS/Data REST closure. Docker Compose stock-delta smoke đạt 45/45 hai lần, mandatory flow kết thúc ở 13, checkout/admin contention ở 13, cancel/admin contention ở 9 và cleanup exact-ID đạt sau cả hai lần.

## Đồng Bộ Checkout + Coupon (Backend/Frontend)

- `CheckoutOrderRequest` hỗ trợ thêm `maCoupon`.
- `OrderServiceImpl` tính `soTienGiam` và `tongTien` phía backend (không tin tổng tiền từ frontend).
- `CheckoutOrderResponse` trả thêm các trường tóm tắt đơn: `tongTienSanPham`, `soTienGiam`, `maCoupon`, `phuongThucThanhToan`, cùng thông tin nhận hàng.
- Coupon vẫn giữ chính sách **chỉ cho user đã đăng nhập** qua endpoint `/api/coupon/kiem-tra` (được cấu hình nhóm authenticated trong `security/Endpoints.java`).
- Tránh race condition khi redeem coupon bằng update có điều kiện nguyên tử trong `CouponRepository.tangLuotSuDungNeuConHieuLuc(...)`.

## Trạng Thái Đơn Hàng (ánh xạ số ↔ nghĩa)

Hai cột `Integer` trên `don_hang` giữ nguyên kiểu số; ánh xạ nghĩa được xác minh từ toàn bộ `book_FE` và backend:

| Cột | Giá trị | Nghĩa | Nơi đọc/ghi |
|---|---|---|---|
| `trang_thai_thanh_toan` | 0 | Chưa thanh toán | checkout khởi tạo 0 (`OrderServiceImpl`); FE badge `=== 0` → "Chưa thanh toán" |
| | 1 | Đã thanh toán | VNPay callback set 1 (`DonHangController` `/vnpay-payment`); FE badge còn lại → "Đã thanh toán" |
| `trang_thai_giao_hang` | 0 | Chờ xử lý | checkout khởi tạo 0 (COD **và** VNPay) |
| | 1 | Đang giao / đã xác nhận | VNPay callback set 1; COD lên 1 qua endpoint admin `cap-nhat-trang-thai-giao-hang` |
| | 2 | Đã giao | endpoint admin advance lần 2; FE badge `=== 2` → "Đã nhận hàng"/"delivered" |
| | 3 | Đã hủy (mới) | user/admin hủy đơn; FE hiện render như "Đang xử lý" cho tới khi thêm badge (follow-up FE) |

Seed demo (`V4`): giá trị `trang_thai_giao_hang ∈ {0,1,2}`, `trang_thai_thanh_toan ∈ {0,1}` — không có giá trị lạ, `3` không đụng bất kỳ so sánh nào trong FE.

**Hai cơ chế chuyển trạng thái giao hàng** (mọi thay đổi đi qua `DonHangTrangThaiService`):
- **Chuyển có đích** (`chuyenTrangThaiGiaoHang(don, target, ...)`): idempotent no-op khi `target == hiện tại`; hợp lệ `0→1`, `0→3`, `1→3`. Dùng cho VNPay callback (target=1, an toàn khi gọi lại) và hủy đơn (target=3).
- **Tiến 1 bước** (`chuyenTrangThaiTiepTheo(don, ...)`): `0→1`, `1→2`; ở `2`/`3` → 409. Là đường DUY NHẤT đưa đơn COD tới "đã giao"; chỉ dùng bởi endpoint admin `cap-nhat-trang-thai-giao-hang`.

Thanh toán: `chuyenTrangThaiThanhToan(don, target)` — một chiều `0→1`, idempotent khi gọi lại.

## File Lớn Nhất (theo LOC)

1. `db/migration/V1__init_schema.sql` - ~230 dòng (full schema)
2. `sach/service/SachServiceImpl.java` - ~367 dòng
3. `donhang/web/DonHangController.java` - ~219 dòng
4. `thanhtoan/config/VnPayConfig.java`
5. `thanhtoan/service/VNPayService.java`

## Những Vấn Đề Đã Biết

1. **Unimplemented Methods**: Một vài service stub (`AdminUserServiceImpl`, `SachServiceImpl.delete`) trả về `null`.
2. **HTML Sanitization**: `BookDescriptionSanitizer` (`shared/util/`) dùng regex thay vì thư viện chuyên dụng.

### Đã khắc phục

- **Public admin endpoints** (`/api/admin/user**`, `/api/admin/sach**` từng nằm trong `PUBLIC_GET`): siết lại ở nhánh `security-hardening` (PR #1).
- **Lộ hash mật khẩu qua JPA entity**: `NguoiDung.matKhau` gắn `@JsonProperty(WRITE_ONLY)`, các field nhạy cảm `@JsonIgnore`; API công khai trả DTO.
- **VNPay callback fail-open** (`"" == ""` khi thiếu secret): thêm guard fail-closed trong `VNPayService.orderReturn`.
- **Duplicate activation method + leftover `main()`** trong `TaiKhoanService`, và `md5`/`Sha256` không dùng trong `VnPayConfig`: đã xóa khi dọn dẹp package-by-feature.
