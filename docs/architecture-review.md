# Đánh Giá & Định Hướng Kiến Trúc: Package-by-Feature

> Tài liệu này là deliverable của Phase 1 (kế hoạch `plans/20260708-package-by-feature`). Nó chốt hiện trạng,
> lý do refactor, cấu trúc đích, và **bảng ánh xạ package cũ→mới** làm kim chỉ nam cho Phase 3–6.
> Cập nhật lần cuối: 2026-07-08.

## 1. Hiện trạng — Package-by-layer

Mã nguồn hiện chia theo **tầng kỹ thuật**:

```
com.example.book_be/
├── controller/ (+admin/)   # 12 + 8 file
├── services/   (+admin,cart,email,review,JWT/)  # ~33 file
├── dao/                    # 15 repository
├── entity/                 # 18 entity
├── dto/ (cart, theloai)    # 14 file (chỉ 2 feature có DTO)
├── bo/                     # 7 Business Object
├── config/ security/ util/ # cross-cutting
```

Tổng ~118 file Java, 18 entity, ~12 nghiệp vụ.

## 2. Vấn đề (thẳng)

1. **Một nghiệp vụ nằm rải rác 6–7 package** → khó đọc, khó bảo trì (package-by-layer wall).
2. **Chiến lược DTO bất nhất**: tồn tại *cả* `bo/` lẫn `dto/`; DTO chỉ có cho `cart` + `theloai`, phần còn lại
   **trả thẳng JPA entity** — chính là nguyên nhân lỗ hổng lộ hash mật khẩu (đã sửa, merge PR #1).
3. **Vi phạm quy ước**: package `services/JWT` viết HOA; `dao/` là tên cũ (nên là `repository/`).
4. **Trộn ngôn ngữ**: `Sach/DonHang/TheLoai` (Việt) lẫn `Cart/Order/Review/Coupon` (Anh).
5. **`VnPayConfig`** gộp config + secret tĩnh + hàm crypto trong một class static.

## 3. So sánh 3 mô hình

| Mô hình | Bản chất | Hợp với dự án? |
|---|---|---|
| Package-by-layer (hiện tại) | Chia theo tầng | Nhập môn, cohesion thấp |
| **Package-by-feature hybrid** ⭐ | Chia theo nghiệp vụ, phân tầng bên trong | **Chọn** — cân bằng tốt nhất cho app cỡ vừa |
| Spring Modulith | Feature module + ép ranh giới bằng test | Cân nhắc sau khi app lớn hơn |
| Hexagonal / Clean / DDD | domain/application/infrastructure tách framework | Overkill cho CRUD bán sách (YAGNI) |

**Quyết định:** package-by-feature hybrid. Không dùng Hexagonal/DDD (over-engineering). Spring Modulith/ArchUnit
để ngỏ như tùy chọn Phase 7.

## 4. Cấu trúc đích

```
com.example.book_be/
├── BookBeApplication.java
├── sach/        web/ service/ repository/ domain/ dto/   # catalog
├── nguoidung/   ... + baomat/(jwt)                       # user + auth
├── yeuthich/
├── giohang/
├── donhang/
├── thanhtoan/                                            # VNPay
├── danhgia/
├── giamgia/
├── seo/
├── thongke/
└── shared/      config/ security/ util/ dto/             # cross-cutting
```

Bố cục mỗi feature: `web/` (controller) · `service/` · `repository/` · `domain/` (entity) · `dto/`.

## 5. Quy ước đặt tên — chuẩn tiếng Việt

Mở rộng `docs/code-standards.md` (đã quy định entity/field/path tiếng Việt) sang **tên feature + class**.

| Hiện tại (Anh) | Đổi thành (Việt) | Ghi chú |
|---|---|---|
| `CartService(Impl)` | `GioHangService` | rename ở đợt riêng (RT-6) |
| `OrderService(Impl)` | `DonHangService` | rename ở đợt riêng (RT-6) |
| `Coupon` (entity) | `MaGiamGia` | cần Flyway migration — **open question** |
| package `services/JWT` | `nguoidung/baomat` | package phải chữ thường |
| `dao/` | `<feature>/repository/` | bỏ tên DAO |
| `*Bo` | `*Dto` trong `<feature>/dto/` | rename ở đợt riêng (RT-6) |

## 6. Bảng ánh xạ package cũ → mới (kim chỉ nam Phase 3–6)

### Entity (`entity/` → `<feature>/domain/`)

| Entity | Feature đích |
|---|---|
| `Sach`, `TheLoai`, `HinhAnh`, `NhaCungCap`, `SachThongTinChiTiet` | `sach/domain/` |
| `NguoiDung`, `DiaChiGiaoHang`, `Quyen` | `nguoidung/domain/` |
| `SachYeuThich` | `yeuthich/domain/` |
| `GioHang` | `giohang/domain/` |
| `DonHang`, `ChiTietDonHang`, `HinhThucGiaoHang` | `donhang/domain/` |
| `HinhThucThanhToan` | `thanhtoan/domain/` |
| `SuDanhGia` | `danhgia/domain/` |
| `Coupon`, `LoaiGiamGia` | `giamgia/domain/` |
| `ThongBao` (DTO thông báo) | `shared/dto/` |

### Repository (`dao/` → `<feature>/repository/`)

Mỗi `*Repository` đi theo entity tương ứng: `SachRepository`→`sach/repository/`, `NguoiDungRepository`→`nguoidung/repository/`,
`GioHangRepository`→`giohang/repository/`, `DonHang/ChiTietDonHang/HinhThucGiaoHang Repository`→`donhang/repository/`,
`HinhThucThanhToanRepository`→`thanhtoan/repository/`, `SuDanhGiaRepository`→`danhgia/repository/`,
`CouponRepository`→`giamgia/repository/`, `SachYeuThichRepository`→`yeuthich/repository/`.

### Controller (`controller/`, `controller/admin/` → `<feature>/web/`)

| Controller | Feature |
|---|---|
| `SachUserController`, `admin/SachController`, `TheLoaiController`, `admin/AdminTheLoaiController` | `sach/web/` |
| `TaiKhoanController`, `NguoiDungController`, `DiaChiController`, `admin/UserController`, `admin/QuyenController` | `nguoidung/web/` |
| `YeuThichController` | `yeuthich/web/` |
| `GioHangController` | `giohang/web/` |
| `DonHangController`, `admin/DonHangAdminController` | `donhang/web/` |
| `DanhGiaController`, `admin/BinhLuanController` | `danhgia/web/` |
| `CouponController`, `admin/CouponAdminController` | `giamgia/web/` |
| `SeoController`, `SitemapController` | `seo/web/` |
| `admin/ThongKeController` | `thongke/web/` |

### Service (`services/` → `<feature>/service/`)

| Service | Feature |
|---|---|
| `SachService(Impl)`, `admin/SachService(Impl)`, `TheLoaiService(Impl)`, `CloudinaryService`, `admin/BookImageStorageService` | `sach/service/` |
| `TaiKhoanService`, `NguoiDungService(Impl)`, `UserService(Impl)`, `DiaChiService(Impl)`, `admin/AdminUserService(Impl)`, `email/EmailService(Impl)` | `nguoidung/service/` |
| `cart/CartService(Impl)` | `giohang/service/` |
| `cart/OrderService(Impl)` | `donhang/service/` |
| `VNPayService` | `thanhtoan/service/` |
| `review/DanhGiaService(Impl)` | `danhgia/service/` |
| `CouponService(Impl)` | `giamgia/service/` |
| `SeoService(Impl)` | `seo/service/` |
| `ThongKeService(Impl)` | `thongke/service/` |
| `JWT/JwtService`, `JWT/Jwtfilter` | `nguoidung/baomat/` (chữ thường) |

### DTO / BO (`dto/`, `bo/` → `<feature>/dto/`)

| Nguồn | Feature |
|---|---|
| `bo/SachBo`, `bo/SachAdminUpsertBo`, `bo/SachThongTinChiTietBo`, `dto/theloai/*` | `sach/dto/` |
| `bo/UserBo`, `bo/PhanQuyenBo` | `nguoidung/dto/` |
| `dto/cart/CartItem*`, `CartMerge*`, `CartQuantity*`, `CartSummary*`, `CartLineAdjustment*` | `giohang/dto/` |
| `dto/cart/CheckoutOrder*`, `OrderListItemResponse`, `VNPayUrlResponse` | `donhang/dto/` |
| `bo/DanhGiaBo` | `danhgia/dto/` |
| `bo/BaseBo` | `shared/dto/` (nếu dùng chung) |

### Cross-cutting (`config/`, `security/`, `util/` → `shared/`)

| Nguồn | Đích |
|---|---|
| `config/RestConfig`, `config/CloudinaryConfig` | `shared/config/` |
| `config/VnPayConfig` | `thanhtoan/` (nghiệp vụ, KHÔNG shared) |
| `security/SecurityConfiguration`, `Endpoints`, `JwtResponse`, `LoginRequest` | `shared/security/` |
| `util/SlugUtil`, `util/BookDescriptionSanitizer` | `shared/util/` |

## 7. Bất biến bắt buộc (từ Red-Team)

- **[RT-4]** KHÔNG đổi `@RequestMapping` path khi move (phân quyền path-based); chạy `SecuritySmokeTest` sau mỗi phase.
- **[RT-6]** Đợt này chỉ **MOVE** (đổi package). Rename class + sửa logic (stub `null`, static→bean) tách đợt riêng.
- **[RT-5]** Cổng ROI sau Phase 3 — dừng đánh giá trước khi làm tiếp.
- **[RT-3]** Test dùng MySQL thật (Testcontainers/compose), KHÔNG H2.
- **[RT-2]** Test assert field ổn định, không so JSON toàn phần.

## 8. Nguồn tham khảo

- Spring Boot layer vs feature: <https://rifaiio.medium.com/spring-boot-project-structure-best-practices-layer-based-vs-feature-based-explained-simply-4a9002f3cff0>
- Hexagonal + DDD + Spring: <https://www.baeldung.com/hexagonal-architecture-ddd-spring>
- Spring Modulith: <https://bell-sw.com/blog/what-is-spring-modulith-introduction-to-modular-monoliths/>
