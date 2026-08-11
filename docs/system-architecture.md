# Kiến Trúc Hệ Thống

## Tổng Quan

```
┌─────────────────┐     ┌──────────────────┐     ┌─────────────┐
│   Frontend      │     │    Backend        │     │   MySQL 8.0 │
│   React :3000   ├────►│  Spring Boot     ├────►│   :3306     │
│                 │HTTP  │    :8080         │ JPA │             │
└─────────────────┘     └──────────────────┘     └─────────────┘
                              │      │
                              │      │
                         ┌────┘      └────┐
                         ▼                ▼
                  ┌────────────┐   ┌────────────┐
                  │   VNPay    │   │   Gmail    │
                  │  Sandbox   │   │   SMTP    │
                  └────────────┘   └────────────┘
```

## Luồng Xác Thực (Authentication Flow)

```
Client                    Backend                      DB
  │                          │                          │
  ├──POST /tai-khoan/dang-ky─►│                          │
  │  {tenDangNhap, matKhau,  │──BCrypt hash password───►│
  │   email...}              │──Lưu NguoiDung──────────►│
  │                          │──Gửi email kích hoạt────►│ Gmail
  │◄─────200 OK──────────────┤                          │
  │                          │                          │
  ├──GET /tai-khoan/kich-hoat─►│                          │
  │  ?email=x&maKichHoat=y   │──Verify & activate──────►│
  │◄─────200 OK──────────────┤                          │
  │                          │                          │
  ├──POST /tai-khoan/dang-nhap►│                          │
  │  {username, password}    │──Rate limit check        │
  │                          │──AuthenticationManager   │
  │                          │  .authenticate()─────────►│
  │                          │──JwtService              │
  │                          │  .generateToken()        │
  │◄─────{jwt: "eyJ..."}────┤                          │
  │                          │                          │
  ├──GET /api/don-hang/findAll►│                          │
  │  Authorization: Bearer jwt│──JwtFilter              │
  │                          │  .extractUsername()      │
  │                          │  .validateToken()        │
  │                          │──SecurityContext set     │
  │                          │──Controller xử lý───────►│
  │◄─────Response────────────┤                          │
```

## Luồng Thanh Toán VNPay

```
Client              Backend              VNPay Sandbox
  │                    │                       │
  ├─GET /submitOrder──►│                       │
  │ ?maDonHang         │──Load DonHang từ DB   │
  │                    │  (check owner, status)│
  │                    │  Nếu COD: reject      │
  │                    │──createOrder()        │
  │                    │  amount lấy từ tongTien DB
  │                    │  orderInfo = maDonHang
  │                    │  HMAC-SHA512 sign     │
  │◄──VNPay URL────────┤                       │
  │                    │                       │
  ├──Redirect to VNPay─────────────────────────►│
  │                    │                       │
  │◄──Redirect callback (vnp_ResponseCode)─────┤
  │                    │                       │
  ├─GET /vnpay-payment►│                       │
  │ ?vnp_ResponseCode  │──orderReturn()        │
  │ &vnp_Amount...     │  Verify checksum      │
  │                    │  Verify amount == tongTien*100
  │                    │──Cập nhật đơn hàng───►│ DB
  │                    │──Gửi email xác nhận──►│ Gmail
  │◄──"ordersuccess"───┤                       │
```

## Luồng Đặt Hàng

```
Client                     Backend                          DB
  │                           │                              │
  │  (Đã đăng nhập)          │                              │
  ├──POST /api/don-hang/them─►│                              │
  │  Header: Idempotency-Key  │  (bat buoc; thieu -> 400)    │
  │  Body: {                  │──SecurityContext.getAuth()   │
  │    items: [{maSach,soLuong}],                            │
  │    maDiaChiGiaoHang,                                     │
  │    phuongThucThanhToan(COD|VNPAY),                       │
  │    maCoupon?                                              │
  │  }                        │──Validate request            │
  │                           │  bắt buộc địa chỉ + payment  │
  │                           │──Check address ownership────►│
  │                           │──resolve payment by ma_code─►│
  │                           │──validate coupon (nếu có)───►│
  │                           │──Tính giảm giá tại backend    │
  │                           │──Tạo DonHang + ChiTiet──────►│
  │◄──CheckoutOrderResponse───┤  gồm tongTienSanPham,         │
  │                           │  soTienGiam, maCoupon         │
  │                           │                              │
  │  (Không đăng nhập)        │                              │
  ├──POST /api/don-hang/them──►│                              │
  │                           │──Security từ chối (401)─────►│
  │◄──ApiError UNAUTHENTICATED┤                              │
```

> **Không có guest checkout.** Endpoint `them-don-hang-moi` cùng matcher public
> và trang `DatHangNhanh` đã bị xóa; mọi lần tạo đơn đều cần JWT hợp lệ và một
> địa chỉ giao hàng thuộc sở hữu của chính người dùng đó.

> **Tồn kho nguyên tử:** checkout trừ kho bằng UPDATE có điều kiện `SachRepository.truKhoNeuDu` (0 bản ghi = hết hàng → 400, rollback cả đơn). Lặp trừ theo `maSach` tăng dần (TreeMap) để chống deadlock. Query chỉ nhận số lượng dương và không làm tồn âm.

## Giỏ Hàng Authoritative Trên Server

`/api/gio-hang/**` là API giỏ hàng của người dùng đã xác thực. Repository Spring
Data REST `GioHangRepository` vẫn `exported=false`; chỉ controller này được phục
vụ dữ liệu. Giỏ local chỉ là nguồn nhập tạm cho khách trước đăng nhập, không phải
nguồn sự thật sau đó.

| Method | Path | Hành vi |
|---|---|---|
| `GET` | `/api/gio-hang` | Trả summary `{items, tongSoLuong, tongTien}` của user hiện tại. |
| `POST` | `/api/gio-hang/items` | Body `{maSach, soLuong}`; cộng vào dòng hiện có. |
| `PUT` | `/api/gio-hang/items/{maSach}` | Body `{soLuong}`; số dương thay số lượng, `0` xóa. |
| `DELETE` | `/api/gio-hang/items/{maSach}` | Xóa một dòng. |
| `DELETE` | `/api/gio-hang` | Xóa toàn bộ giỏ. |
| `POST` | `/api/gio-hang/merge` | Nhập giỏ khách `{items:[{maSach, soLuong}]}`; bắt buộc `Idempotency-Key`. |

Mọi ghi giỏ và checkout cùng lấy `PESSIMISTIC_WRITE` trên row `NguoiDung`; vì vậy
mỗi người dùng có một luồng ghi tuần tự cho add/update/remove/merge/clear và đặt
hàng. Ràng buộc DB `uk_gio_hang_nguoi_sach` bảo đảm tối đa một dòng `(ma_nguoi_dung,
ma_sach)`; `so_luong` là `INT NOT NULL` với check `> 0`. Migration V19 dọn các dòng
legacy không hợp lệ, gộp dòng trùng và cap tổng tại `Integer.MAX_VALUE` trước khi
siết các ràng buộc này.

### Merge giỏ khách idempotent

Client phải gửi `Idempotency-Key` không rỗng, dài tối đa 100 ký tự, chỉ gồm chữ/số
và `.`, `_`, `-`; thiếu hoặc sai trả `400`. Filter trước MVC binding yêu cầu
`Content-Length` hợp lệ và tối đa 32 KiB; request nhận tối đa 100 dòng. Mỗi
instance nhận tối đa 30 merge mới/10 phút/người dùng; replay receipt đã tồn tại được
xử lý trước rate limit để response-loss retry luôn an toàn. Server gộp các dòng khách
theo `maSach`, kiểm tra số lượng dương và băm payload chuẩn hóa. Trong một transaction,
server:

1. khóa user và tra receipt theo `(ma_nguoi_dung, idempotency_key)`;
2. nếu receipt có fingerprint trùng, trả lại nguyên snapshot response đã lưu, không ghi giỏ;
3. nếu fingerprint khác, trả `409`, không ghi giỏ;
4. nếu chưa có receipt, áp dụng merge, lưu response JSON và receipt rồi commit.

`gio_hang_merge_receipt` có PK, unique `(ma_nguoi_dung, idempotency_key)`, fingerprint
SHA-256 không null, index `(created_at, receipt_id)` và FK tới user `ON DELETE CASCADE`.
Receipt sống cùng user, bị xóa khi user bị xóa và được job hằng ngày dọn sau 30 ngày
theo transaction batch 500; đây cũng là cửa sổ replay được cam kết. Khóa user khiến
hai merge cùng user/key tuần tự; chỉ request đầu tiên thay đổi
giỏ, request sau replay hoặc conflict.

Merge cộng vào dòng server rồi cap theo tồn kho. Sách không tìm thấy, inactive hoặc
hết hàng không được thêm và được báo ở `removedItems` với `BOOK_NOT_FOUND`,
`BOOK_INACTIVE` hoặc `OUT_OF_STOCK`; dòng bị cap được báo ở `adjustedItems` với
`CAPPED_TO_STOCK`. Các thao tác thêm/cập nhật trực tiếp từ chối sách inactive, hết
hàng, ID/số lượng không hợp lệ hoặc lượng vượt tồn kho bằng `400`. Summary cũng bỏ
qua dòng trở nên inactive hoặc hết hàng để không quảng bá hàng không thể mua.

### Checkout và dọn giỏ

Checkout dùng cùng khóa user, trừ kho và tạo đơn trong transaction. Sau khi tạo từng
`ChiTietDonHang`, chỉ dòng giỏ có `(ma_nguoi_dung, ma_sach)` đã có trong đơn mới bị
xóa; các dòng khác của giỏ được giữ. Replay checkout không chạy lại transaction nên
không xóa thêm dòng giỏ. Không có guest checkout.

## Luồng Tồn Kho và Điều Chỉnh Admin

`Sach.soLuong` luôn phải nằm trong miền `0..Integer.MAX_VALUE`. Bốn nguồn ghi kho hợp lệ là tạo sách (tồn ban đầu), checkout (trừ), hủy đơn (hoàn), và delta quản trị. Mọi thay đổi runtime dùng `UPDATE` có điều kiện trong transaction, không đọc-rồi-ghi giá trị tồn.

```text
Create admin                         Metadata PUT
POST /api/admin/sach/insert          PUT /api/admin/sach/update/{id}
soLuongTon >= 0 ───────► Sach        soLuongTon legacy ─► ignored
                                                 │
Checkout                              @DynamicUpdate chỉ flush metadata dirty columns
POST /api/don-hang/them
items (+, aggregate <= int max)
          │
          ▼
truKhoNeuDu: soLuong >= requested ──────────────► soLuong - requested

Cancel order                           Admin stock adjustment
POST /api/don-hang/huy/{id}            PATCH /api/admin/sach/{id}/ton-kho
hoanKho: soLuong <= MAX - restore      {"soLuongThayDoi": signed int != 0}
          │                                           │
          └────────────── bounded atomic update ◄─────┘
                                      │
                                      ▼
                         {"maSach": id, "soLuongTon": authoritative}
```

- **Create:** `POST /api/admin/sach/insert` validates and writes `soLuongTon >= 0` as initial stock.
- **Metadata:** `PUT /api/admin/sach/update/{id}` preserves metadata, images, categories, and details but ignores legacy `soLuongTon`, including stale, `null`, and negative values. `Sach` has `@DynamicUpdate` so the resulting managed-entity flush does not write an unchanged stock column.
- **Checkout:** duplicate book lines are aggregated in a `TreeMap`; a non-positive item quantity or aggregate above `Integer.MAX_VALUE` is rejected. The conditional decrement prevents oversell and rolls back the order if any line cannot be fulfilled.
- **Cancellation:** restore is conditional on `current <= Integer.MAX_VALUE - restore`; a bound conflict returns 409 and rolls back the cancellation transaction.
- **Admin delta:** positive and negative requests use separate bounded conditional queries. The service reads the persisted scalar after a successful update and returns it as the authoritative response; it does not rely on a stale entity cache.

## HTTP Contract Điều Chỉnh Tồn Kho

| Method | Path | Authorization | Request | Success | Failure |
|---|---|---|---|---|---|
| `PATCH` | `/api/admin/sach/{id}/ton-kho` | ADMIN only | `{"soLuongThayDoi": integer khác 0}` | `200`, `{"maSach":integer,"soLuongTon":integer}` | `400` body missing/invalid/zero/malformed; `404` book missing; `409` below-zero or `int` range conflict; `401/403` non-admin |

CORS permits `PATCH` only from the normalized `FRONTEND_URL` origin. The security matcher is path-based and requires `ADMIN` for `PATCH /api/admin/sach/**`.

Spring Data REST preserves GET on the `Sach` surfaces but disables `POST`, `PUT`, `PATCH`, and `DELETE` at the collection, item, and association level. This prevents raw Data REST writes from bypassing the stock-delta contract.

`SuDanhGiaRepository` is `@RepositoryRestResource(exported = false)`, so `/su-danh-gia/**` and the relation `/sach/{id}/listDanhGia` both return 404. That relation used to be open and served admin-hidden reviews — complete with `"isActive": 0` — to anonymous callers, because it never passes through `DanhGiaController` and therefore honours no status filter. The only public read path for reviews is `GET /api/danh-gia?maSach=`, which filters `trangThai = HIEN_THI`. Blocking the repository rather than the `Sach` association was deliberate: association-exposure config is keyed on the *owning* domain type, so disabling it on `Sach` would also have killed `/sach/{id}/listTheLoai`.

## Luồng Trạng Thái & Hủy Đơn Hàng

Hai cột `Integer` (`trang_thai_thanh_toan` 0/1, `trang_thai_giao_hang` 0=chờ,1=đang giao,2=đã giao,3=đã hủy) giữ nguyên kiểu; mọi thay đổi đi qua `DonHangTrangThaiService`, mỗi chuyển hợp lệ ghi 1 dòng `lich_su_trang_thai_don_hang`.

- **VNPay callback** → `chuyenTrangThaiGiaoHang(DANG_GIAO)` + `chuyenTrangThaiThanhToan(DA_THANH_TOAN)` — idempotent, an toàn khi callback chạy lại.
- **Admin `cap-nhat-trang-thai-giao-hang`** → `chuyenTrangThaiTiepTheo` (tiến 1 bước: 0→1→2; ở 2/3 → 409). Đơn COD cần 2 lần bấm để tới "đã giao". Ràng buộc theo phương thức thanh toán:
  - **VNPAY (trả trước):** đơn `chưa thanh toán` → **chặn giao (409)**.
  - **COD (trả khi nhận):** khi lên `DA_GIAO (2)` → tự động set `trang_thai_thanh_toan=1` (đã thu tiền mặt) để doanh thu tính đúng. (Đơn có phương thức thanh toán `null` cũng được coi như COD.)
- **Hủy đơn** `POST /api/don-hang/huy/{maDonHang}` (owner hoặc admin):
  1. Kiểm quyền (owner/admin) + trạng thái (user chỉ hủy khi `CHO_XU_LY`; admin `CHO_XU_LY`/`DANG_GIAO`); user không hủy được đơn đã thanh toán online (400).
  2. `chuyenTrangThaiGiaoHang(DA_HUY)` **trước** — `@Version` optimistic lock chặn double-cancel (request thứ 2 → 409, không hoàn kho/coupon 2 lần).
  3. Hoàn kho (`hoanKho`) + hoàn lượt coupon (`giamLuotSuDung`) trong cùng transaction.
  4. Email xác nhận gửi qua `afterCommit` (ngoài transaction — SMTP lỗi không rollback hủy đơn).
- Doanh thu (`sumDoanhThu`/`sumDoanhThuHomNay`) tính đơn `trang_thai_thanh_toan=1` và **loại trừ** đơn `trang_thai_giao_hang=3` (đã hủy). Vì COD được set `thanh_toan=1` khi giao xong, doanh số COD đã giao được tính đúng.
- **Giả định (YAGNI):** "COD giao=2 ⟹ đã thu tiền". Nếu sau này thêm trạng thái "giao thất bại", phải xem lại chỗ auto-set thanh toán trong `chuyenTrangThaiTiepTheo`.
- `findAll`/`findById`: admin thấy mọi đơn; user chỉ thấy đơn của chính mình.

## Phân Quyền API

### Endpoint Công Khai (Public)

| Method | Path | Mô tả |
|--------|------|-------|
| GET | `/sach/**`, `/hinh-anh/**` | Xem sách, hình ảnh |
| GET | `/api/sach**`, `/api/sach/search` | Tìm kiếm sách |
| GET | `/api/the-loai`, `/api/the-loai/{slug}` | Danh sách thể loại public và chi tiết theo slug |
| POST | `/tai-khoan/dang-ky` | Đăng ký |
| POST | `/tai-khoan/dang-nhap` | Đăng nhập |
| GET | `/tai-khoan/kich-hoat` | Kích hoạt tài khoản |
| ALL | `/gio-hang/**` | Matcher legacy; repository `GioHang` đã `exported=false` nên không phục vụ dữ liệu. API giỏ hàng hiện hành là `/api/gio-hang/**` và yêu cầu JWT. |
| GET | `/api/danh-gia` | Xem đánh giá (phân trang, phân bố sao) |
| GET | `/api/don-hang/vnpay-payment` | Callback VNPay |

### Endpoint Yêu Cầu Đăng Nhập (Authenticated)

| Method | Path | Mô tả |
|--------|------|-------|
| GET | `/api/gio-hang` | Xem summary giỏ server của chính user |
| POST | `/api/gio-hang/items` | Thêm `{maSach, soLuong}` vào giỏ server |
| PUT | `/api/gio-hang/items/{maSach}` | Đặt số lượng; `0` xóa dòng |
| DELETE | `/api/gio-hang/items/{maSach}` | Xóa một dòng giỏ |
| DELETE | `/api/gio-hang` | Xóa toàn bộ giỏ |
| POST | `/api/gio-hang/merge` | Merge giỏ khách; bắt buộc `Idempotency-Key`, replay cùng payload trả snapshot, payload khác trả `409` |
| POST | `/api/don-hang/them` | Đặt hàng; bắt buộc `Idempotency-Key`, nhận `maCoupon` (optional), backend tự tính giảm giá và tổng tiền |
| GET | `/api/don-hang/findAll**` | Xem đơn hàng (admin thấy mọi đơn; user chỉ đơn của mình) |
| GET | `/api/don-hang/{id}` | Chi tiết đơn (admin mọi đơn; user chỉ đơn của mình → 403) |
| POST | `/api/don-hang/huy/{maDonHang}` | Hủy đơn (owner hoặc admin); hoàn kho + coupon, chống double-cancel |
| GET | `/api/don-hang/submitOrder**` | Tạo link VNPay theo `maDonHang` + `tongTien` backend; từ chối đơn COD |
| POST | `/api/coupon/kiem-tra` | Kiểm tra coupon cho user đã đăng nhập |
| POST | `/api/danh-gia/them-danh-gia-v1` | Thêm đánh giá |

### Endpoint Admin

| Method | Path | Mô tả |
|--------|------|-------|
| POST | `/api/admin/sach/insert` | Thêm sách |
| PUT | `/api/admin/sach/update/**` | Sửa metadata sách; `soLuongTon` legacy bị bỏ qua |
| PATCH | `/api/admin/sach/{id}/ton-kho` | Điều chỉnh tồn kho delta nguyên tử; chỉ ADMIN |
| POST | `/api/admin/sach/active/**` | Kích hoạt sách |
| POST | `/api/admin/sach/unactive/**` | Vô hiệu hóa sách |
| GET | `/api/admin/quyen/findAll` | Danh sách quyền |
| POST | `/api/admin/user/phan-quyen` | Phân quyền |
| GET | `/api/admin/the-loai` | Danh sách thể loại cho admin |
| POST | `/api/admin/the-loai` | Tạo thể loại mới, tự sinh slug |
| PUT | `/api/admin/the-loai/{maTheLoai}` | Cập nhật tên thể loại và slug |
| DELETE | `/api/admin/the-loai/{maTheLoai}` | Xóa thể loại khi chưa gắn sách |
| POST | `/api/don-hang/cap-nhat-trang-thai-giao-hang/**` | Tiến 1 bước trạng thái giao hàng (0→1→2); ở 2/3 → 409 |
| GET | `/api/admin/danh-gia/findAll**` | Xem đánh giá (admin) |
| POST | `/api/admin/danh-gia/active/**` | Duyệt đánh giá |
| POST | `/api/admin/danh-gia/unactive/**` | Ẩn đánh giá |

## Cấu Hình Bảo Mật

```
SecurityConfiguration
├── BCryptPasswordEncoder         # Hash mật khẩu
├── DaoAuthenticationProvider     # Xác thực từ DB
├── SecurityFilterChain
│   ├── Phân quyền endpoint (authorizeHttpRequests)
│   ├── CORS config (`FRONTEND_URL`, normalized exact origin)
│   ├── JWT filter (trước UsernamePasswordAuthenticationFilter)
│   ├── Session: STATELESS
│   └── CSRF: disabled (dùng JWT)
└── AuthenticationManager
```

## Database Migration (Flyway)

```
App Startup:
  1. DataSource connect → MySQL
  2. Flyway check flyway_schema_history
  3. Flyway apply pending migrations (V1→V19)
  4. Hibernate validate schema vs entities
  5. Application context boot
```

- Schema quản lý bởi Flyway, **không** dùng `ddl-auto=update`
- Hibernate chỉ `validate` — phát hiện drift mà không tự sửa DB
- `V5__add_slug_to_the_loai.sql` thêm cột `slug`, backfill dữ liệu cũ, và tạo unique constraint cho bảng `the_loai`
- `V6__add_payment_method_codes.sql` thêm `ma_code` cho `hinh_thuc_thanh_toan` và backfill mã ổn định (`COD`, `VNPAY`)
- `V7__lich_su_trang_thai_va_ma_coupon.sql` thêm bảng `lich_su_trang_thai_don_hang`, cột `don_hang.ma_coupon` (FK `ON DELETE SET NULL`) và `don_hang.version` (`@Version` optimistic lock)
- `V8__add_join_table_primary_keys.sql` thêm composite primary key (và FK còn thiếu) cho `nguoidung_quyen`/`sach_theloai` — bắt buộc trên managed MySQL có `sql_require_primary_key=ON` (Aiven). Idempotent qua `information_schema` check nên an toàn cho cả DB mới và DB đã chạy V1 trước đó. `db/migration/beforeMigrate.sql` precreate hai bảng này với PK ngay từ đầu cho DB mới (không cần quyền `SESSION_VARIABLES_ADMIN`)
- `V19__server_cart_integrity_and_merge_receipts.sql` dọn dữ liệu giỏ legacy, thêm unique user-sách và check số lượng dương, rồi tạo receipt merge idempotent theo user/key.
- Demo data tự động seed khi DB trống

## Cấu Hình Docker

```yaml
Services:
  mysql:      MySQL 8.0, port 3306, empty root password
              Healthcheck: mysqladmin ping
              (Flyway tạo schema, không cần initdb scripts)

  backend:    Spring Boot (multi-stage Maven build)
              Port: 8080, depends_on: mysql (healthy)
              Env: DB_URL or DB_HOST/DB_PORT/DB_NAME, DB_USERNAME/DB_USER,
                   DB_PASSWORD/MYSQL_PASSWORD, PORT, DDL_AUTO=validate

  frontend:   React build, port 3000
              build context: ../book_FE (repo frontend canonical)
              depends_on: backend
```

## Lịch Sử Xác Minh Build/Runtime Docker

- Bằng chứng lịch sử trước inventory delta ghi nhận backend/frontend image build và stack ba service từng khởi động thành công từ hai repo sibling `book_BE`/`book_FE`.
- Ngày 2026-07-14, stock-delta HTTP smoke trên Compose đạt 45/45 hai lần: mandatory flow kết thúc ở 13, checkout/admin contention ở 13, cancel/admin contention ở 9, bypass routes bị chặn và cleanup exact-ID thành công sau mỗi lần.
- Ngày 2026-07-24, full `mvn -B clean verify` trong Maven-in-Docker gắn Docker Desktop socket, đặt `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal` và process-local `-Dapi.version=1.44` đã chạy Surefire 28/28 cùng bốn lớp Failsafe 28/28 trên MySQL Testcontainers khởi động với `--sql-require-primary-key=ON` (mô phỏng ràng buộc Aiven managed MySQL). `AivenPrimaryKeyMigrationTest` xác nhận V1→V8 chạy sạch trên schema trắng và tạo đúng composite PK cho hai bảng nối. Deterministic concurrency proof đến từ latch/future assertions của integration tests, không từ background HTTP contention.

## Biến Môi Trường

| Biến | Mặc định | Mô tả |
|------|---------|-------|
| `PORT` | `8080` | HTTP port của Spring Boot |
| `DB_URL` | `jdbc:mysql://localhost:3306/web_ban_sach` | JDBC URL ưu tiên |
| `DB_HOST`, `DB_PORT`, `DB_NAME` | `localhost`, `3306`, `web_ban_sach` | Thành phần JDBC fallback |
| `DB_USERNAME` / `DB_USER` | `root` | DB username (`DB_USERNAME` ưu tiên) |
| `DB_PASSWORD` / `MYSQL_PASSWORD` | (trống) | DB password (`DB_PASSWORD` ưu tiên) |
| `FLYWAY_CONNECT_RETRIES` | `10` | Số lần retry kết nối khi database chưa sẵn sàng |
| `FLYWAY_CONNECT_RETRIES_INTERVAL` | `5` | Số giây giữa các lần retry |
| `JWT_SECRET` | Bắt buộc, không có mặc định | Khóa ký JWT Base64 do môi trường runtime cấp |
| `MAIL_USERNAME` | (trống) | SMTP username |
| `MAIL_PASSWORD` | (trống) | SMTP password |
| `FRONTEND_URL` | `http://localhost:3000` | CORS exact origin, email link base, VNPay return base |
| `VNPAY_PAY_URL` | VNPay sandbox payment URL | URL cổng thanh toán HTTP(S), không có query/fragment |
| `VNPAY_API_URL` | VNPay sandbox transaction API | URL API giao dịch HTTP(S), không có query/fragment |
| `VNPAY_RETURN_URL` | `FRONTEND_URL` + `/xu-ly-kq-thanh-toan` | VNPay browser return URL override |
| `VNPAY_TMN_CODE` | (trống) | VNPay merchant code |
| `VNPAY_HASH_SECRET` | (trống) | VNPay secret key |

## Những Vấn Đề Đã Biết (Known Limitations)

### Security & Authorization
Không có giới hạn CORS đã biết trong phạm vi cấu hình hiện tại: `SecurityConfiguration` là nguồn duy nhất, giới hạn exact origin cấu hình được qua `FRONTEND_URL`.

### Incomplete Implementation
1. **Stub Service Methods**: Các method trong `AdminUserServiceImpl` (`save`, `update`, `delete`, `findById`) và `SachServiceImpl.delete()` trả về `null` như stubs.
2. **Admin Order Filtering**: `DonHangAdminController.findAll` có vẻ lọc theo đơn hàng của admin yêu cầu thay vì trả tất cả đơn (có thể là unintended behavior).

### Code Quality
3. **HTML Sanitization**: `BookDescriptionSanitizer` (`shared/util/`) sử dụng regex-based sanitization thay vì library-based HTML parser.

### Đã Khắc Phục
- **Admin endpoint public GET** (`/api/admin/user**`, `/api/admin/sach**`) và **unprotected mutations** ở `SachUserController`: siết lại ở nhánh `security-hardening` (PR #1).
- **Hardcoded defaults** cho `jwt.secret` và VNPay credentials: thay bằng biến môi trường bắt buộc (không còn fallback hardcode).
- **Lộ hash mật khẩu** qua JPA entity: `NguoiDung` ẩn field nhạy cảm; API công khai trả DTO.
- **VNPay callback fail-open** (`"" == ""` khi thiếu secret): thêm guard fail-closed trong `VNPayService.orderReturn`.
- **Duplicate account methods + leftover `main()`** trong `TaiKhoanService`, và `md5`/`Sha256` không dùng trong `VnPayConfig`: đã xóa khi dọn dẹp package-by-feature.
