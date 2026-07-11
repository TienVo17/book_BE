# Tóm Tắt Mã Nguồn

## Thống Kê

- **Số file Java**: 118
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
│   ├── service/      SachService(+Impl), TheLoaiService(+Impl), CloudinaryService, BookImageStorageService
│   ├── repository/   SachRepository, TheLoaiRepository, HinhAnhRepository, NhaCungCapRepository
│   ├── domain/       Sach, TheLoai, HinhAnh, NhaCungCap, SachThongTinChiTiet
│   └── dto/          SachResponse, SachBo, SachAdminUpsertBo, SachThongTinChiTietBo, TheLoai{Response,AdminResponse,AdminUpsertRequest}
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

## Spring Data REST — kết quả xác minh runtime

`RestConfig` để trống toàn bộ block-method (comment), và hầu hết `@RepositoryRestResource` không đặt `exported=false`. Xác minh runtime (curl không token và với token admin) cho thấy **không có lỗ hổng lộ CRUD thô**:

- Path trần không khớp rule bảo mật (`/don-hang`, `/coupon`, `/chi-tiet-don-hang`) → **403 cho mọi caller** (Spring Security default-deny các request không khớp matcher). Không lộ dữ liệu.
- `/nguoi-dung` (trần) khớp `ADMIN_GET_ENDPOINS` → chỉ admin đọc được (403 cho anonymous/USER); response **không** serialize `matKhau`/hash (nhờ `@JsonProperty(WRITE_ONLY)`/`@JsonIgnore` trên `NguoiDung`).
- `/sach` (trần): GET công khai (danh mục sách — không nhạy cảm); POST/PUT/PATCH/DELETE → 403 nếu không phải admin.

Kết luận: `exported=false` chỉ cần trên `GioHangRepository` (đã có); các repo còn lại đã được `SecurityConfiguration` phủ đủ. Không sửa code ở bước này.

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
