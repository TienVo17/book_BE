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
├── donhang/          web/(DonHangController, DonHangAdminController) · service/(OrderService+Impl) · repository/ · domain/(DonHang, ChiTietDonHang, HinhThucGiaoHang) · dto/(CheckoutOrder{Request,Response}, OrderListItemResponse, VNPayUrlResponse)
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
| `src/main/resources/db/migration/` | Flyway migrations (V1-V6): schema, seed, demo data, slug backfill thể loại, payment method codes (`ma_code`) |
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

Schema quản lý bởi Flyway, Hibernate chỉ `validate`. Mọi thay đổi schema phải qua migration mới (V5, V6...).

## Đồng Bộ Checkout + Coupon (Backend/Frontend)

- `CheckoutOrderRequest` hỗ trợ thêm `maCoupon`.
- `OrderServiceImpl` tính `soTienGiam` và `tongTien` phía backend (không tin tổng tiền từ frontend).
- `CheckoutOrderResponse` trả thêm các trường tóm tắt đơn: `tongTienSanPham`, `soTienGiam`, `maCoupon`, `phuongThucThanhToan`, cùng thông tin nhận hàng.
- Coupon vẫn giữ chính sách **chỉ cho user đã đăng nhập** qua endpoint `/api/coupon/kiem-tra` (được cấu hình nhóm authenticated trong `security/Endpoints.java`).
- Tránh race condition khi redeem coupon bằng update có điều kiện nguyên tử trong `CouponRepository.tangLuotSuDungNeuConHieuLuc(...)`.

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
