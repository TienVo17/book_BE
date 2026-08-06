# Project Changelog

## 2026-08-06 (3) — Paged review reads (plan phase 3)

- **`GET /api/danh-gia?maSach=&page=&size=&sort=&loc=` replaces `GET /api/danh-gia/findAll`.** The old endpoint returned every review of a book in one unbounded array; a popular book turned its own product page into a megabyte response. One request now carries the page, `tongSo`, `diemTrungBinh`, and the star distribution. Page size is capped server-side at 50.
- The star distribution and totals are always computed over **all** visible reviews, never over the current filter. Filtering to 4 stars must not zero out the other four bars — that would destroy the very thing the distribution is for. There is a test for exactly that trap.
- Sorting: `moi-nhat`, `cu-nhat`, `diem-cao`, `diem-thap`, and `huu-ich`. `huu-ich` is accepted now and behaves as `moi-nhat` until helpful votes exist, so shared URLs do not break later. Every sort has an explicit id tiebreaker; without one, rows with equal timestamps can appear on two consecutive pages or on none.
- **Split `DanhGiaResponse` into `DanhGiaCongKhaiResponse` and `DanhGiaQuanTriResponse`.** One DTO serving both the shop and the moderation screen is a trap: masking identity for the public path would simultaneously blind the moderator, whose whole job is deciding whether a specific person's post comes down. The public DTO carries no `maNguoiDung` and no `isActive`; the admin DTO keeps real identity plus `trangThai`, `tungBiAn`, and `maDonHang`.
- **Fixed a 500 in admin hide/show.** `SuDanhGia.nguoiDung` is lazy and the entity leaves the transaction before the response is built, so reading the username threw. The admin paths now load through an `@EntityGraph` finder, which also removes an N+1 on the moderation list (21 queries for a 10-row page).
- Nothing in the review domain reads `is_active` any more. The admin list previously declared `useState<any[]>` and read `item.isActive`: with the field gone the value was silently `undefined`, so every row displayed "Đã ẩn" and the button sent `!undefined` — always "show". The moderation tool had inverted itself with no compile error and no failing test. It is now typed `DanhGiaQuanTri[]`, which is what makes `tsc --noEmit` an actual gate. The column itself is still written for compatibility and is dropped in phase 7.

## 2026-08-06 (2) — Verified purchase (plan phase 2)

- **Only customers with a delivered order can review a book.** `POST /api/danh-gia/them-danh-gia-v1` now requires a `don_hang` of the caller, containing that book, with `trang_thai_giao_hang = DA_GIAO (2)`. The review stores that order in `danhgia.ma_don_hang` as its evidence. Cancelled and in-flight orders do not qualify, and another customer's order never unlocks the book for you.
- Added `GET /api/danh-gia/co-the-danh-gia?maSach=` (authenticated) returning `{coThe, maDonHang, lyDo}` with `lyDo ∈ {CHUA_MUA, CHUA_NHAN_HANG, DA_DANH_GIA, DA_BI_AN}`. It is a display aid only — `addReview` re-checks server-side, so a modified client gains nothing. The matching security rule is explicit; without it `anyRequest().denyAll()` would have shipped the endpoint dead.
- **Closed the hide → self-delete → repost loop.** Hiding a review now writes a row in `danhgia_an_tombstone` keyed on `(ma_nguoi_dung, ma_sach)`. The tombstone outlives the review row, so an author who deletes their own hidden review — still their right — cannot post a replacement. `danhgia.tung_bi_an` could not do this job: it dies with the row it lives on.
- Editing a review no longer touches its moderation state, so "edit" is not a cheaper way to unhide than delete-and-repost.
- **Demo orders never reach the admin dashboard.** V12 seeds a `DA_GIAO` order for every legacy review that had no purchase evidence; those rows carry `don_hang.la_don_demo = 1` and are excluded from revenue, today's revenue, total orders, pending orders, and the best-seller table. They also carry `trang_thai_thanh_toan = 0`, so they cannot inflate revenue even if the flag were ignored somewhere.
- `V12__review_verified_purchase.sql` is additive plus DML, re-runnable, and adds `idx_don_hang_nguoi_trang_thai` so the eligibility check is one indexed query per product page.
- `scripts/rehearsal-fixture-reset.sh` now deletes rehearsal reviews before the orders they reference; otherwise the phase-7 foreign key on `ma_don_hang` would fail to create.

## 2026-08-06

### Review foundation (plan phase 1)
- **Reversed the 2026-07-13 decision to keep `GET /sach/{id}/listDanhGia` open.** `SuDanhGiaRepository` is now `@RepositoryRestResource(exported = false)`, so that relation and `/su-danh-gia/**` return 404. Evidence for the reversal: the relation bypasses `DanhGiaController` and therefore every status filter, and was serving admin-hidden reviews — including a literal `"isActive": 0` — to anonymous callers, which made moderation cosmetic. The only public read path for reviews is `GET /api/danh-gia/findAll?maSach=`. `/sach/{id}/listTheLoai` is unaffected.
- Book rating aggregates are now written. `sach.trung_binh_xep_hang` had no writer anywhere in the codebase and only ever held a static seed number; added `sach.so_luot_danh_gia` beside it, and all six write paths (add, update, delete, admin hide, admin show, backfill) recompute inside `@Transactional(rollbackFor = Exception.class)` with `@Modifying(flushAutomatically = true, clearAutomatically = true)`.
- Added `POST /api/admin/danh-gia/tinh-lai-tat-ca` (ADMIN) to recompute every book's aggregate; idempotent.
- Admin hide/show now route through the service instead of writing the repository directly, so moderation recomputes the rating. A non-existent id returns `404 NOT_FOUND` in the unified error schema instead of the previous `500` from an `orElse(null)` NPE.
- One review per user per book, enforced by `uk_danhgia_nguoi_sach`; a duplicate returns `409 CONFLICT` and writes no row. The catch is narrowed to that constraint name so unrelated integrity violations are not mislabelled.
- Fixed a real deadlock between two concurrent reviews of the same book: every mutating path now takes `SachRepository.khoaSachDeCapNhat` (`PESSIMISTIC_WRITE`) before touching `danhgia`, giving a consistent lock order.
- Added a handler for Spring Data REST `ResourceNotFoundException`, which had no handler and returned `500 INTERNAL_ERROR`. Pre-existing: `/sach/{id}/listHinhAnh` had behaved this way since `HinhAnhRepository` became `exported = false`.
- `V11__review_schema_additive.sql`: additive and re-runnable — `trang_thai`, `ma_don_hang`, shop-reply columns, `tung_bi_an`, `sach.so_luot_danh_gia`, a `trung_binh_xep_hang_truoc_v11` rollback column, the `danhgia_huu_ich` and `danhgia_hinh_anh` tables, the unique constraint, and the aggregate backfill. Its one destructive step is documented in the file header: to add the unique constraint it keeps the highest `ma_danh_gia` per `(người, sách)` pair and deletes the rest, unrecoverably. That step is a no-op on current data.
- `FlywayAutoRepairTest` asserted a hardcoded latest version `"10"`, so every new migration would have broken it; it now asserts the invariant that no migration is pending.

## 2026-07-24

### Production connectivity
- Centralized CORS in `SecurityConfiguration` and limited it to the normalized `FRONTEND_URL` origin; removed controller-local and Spring Data REST CORS mappings.
- Made activation, password-reset, and VNPay browser return URLs deployment-configurable.
- Added Render port/database configuration, a Java 17 runtime image, and a secret-safe Blueprint for a Render Free web service connected to Aiven Free MySQL.

## 2026-07-13

### Inventory-delta protection
- Added an ADMIN-only `PATCH /api/admin/sach/{id}/ton-kho` contract. It accepts `{ "soLuongThayDoi": integer khác 0 }` and returns the authoritative scalar `{ "maSach", "soLuongTon" }`; invalid input returns 400, a missing book 404, and lower/upper-range conflicts 409. Non-admin callers receive 401/403.
- Separated book create from metadata update: create still validates/writes initial `soLuongTon >= 0`; metadata `PUT /api/admin/sach/update/{id}` ignores legacy `soLuongTon` even when stale, null, or negative. `Sach` now uses `@DynamicUpdate` so metadata-only dirty flushes do not overwrite stock.
- Bounded all stock writers at `0..Integer.MAX_VALUE`: conditional checkout decrement, cancellation restore, and positive/negative admin delta. Checkout aggregation rejects invalid/overflowing quantities.
- Disabled Spring Data REST `Sach` POST/PUT/PATCH/DELETE at collection, item, and association surfaces while preserving GET `/sach/{id}/listDanhGia`.
- Admin frontend now has separate create/update payloads, read-only current stock, a distinct signed-delta action, server-authoritative response handling, and mandatory stock reload after an ambiguous network outcome.
- Configured `*IT` integration classes for Maven Failsafe during `mvn verify`.

### Verification status — updated 2026-07-14
- Passed: `bash -n scripts/kiem-tra-ton-kho-delta.sh`; frontend production build and TypeScript check; backend/frontend `git diff --check`.
- Full `mvn -B clean verify` in Maven-in-Docker with the Docker Desktop socket, `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal`, and process-local `-Dapi.version=1.44` ran Surefire 14/14 and all 4 Failsafe `*IT` classes 28/28, with 0 failures/errors/skips. Race workers use independent transactions and synchronize only after reading the same initial state, so stock/double-cancel assertions cannot pass by serializing before the critical section; HTTP tests use Java 17's reusable PATCH-capable client.
- `scripts/kiem-tra-ton-kho-delta.sh` passed 45/45 twice on the Compose stack. Both runs observed mandatory final stock 13, checkout/admin contention final 13, cancel/admin contention final 9, blocked bypass routes, and successful exact-ID cleanup.

## 2026-07-08

### Documentation sync pass
- Reconciled all documentation files against current codebase state.
- Fixed JWT expiration time: corrected from "30 phút" to accurate "8 giờ default" (configurable via `JWT_EXPIRATION_MS`).
- Added comprehensive "Known Limitations" sections to project-overview-pdr.md, codebase-summary.md, code-standards.md, and system-architecture.md documenting:
  - Authorization matcher inconsistency (public GET endpoints for `/api/admin/*` paths)
  - Unprotected admin mutation endpoints in SachUserController
  - Stub/unimplemented service methods
  - Duplicate activation methods in TaiKhoanService
  - Regex-based HTML sanitization
  - Hardcoded security credential defaults
- Verified codebase structure, controllers, services, and entity relationships match documentation.

## 2026-03-12

### Coupon and checkout contract alignment
- Aligned frontend and backend coupon validation payload/response for checkout flow.
- Added `maCoupon` to checkout request so backend owns final discount calculation.
- Expanded checkout response with summary fields including `tongTienSanPham`, `soTienGiam`, and `maCoupon`.
- Kept `POST /api/coupon/kiem-tra` as authenticated-only by design.
- Mitigated coupon redemption race condition with atomic conditional usage update in `CouponRepository`.

### Historical runtime and Docker verification
- Historical evidence from this 2026-03-12 release recorded a successful backend Docker build, frontend build from sibling repo context `../book_FE`, and `docker compose up -d` for mysql/backend/frontend.
- This historical result is not runtime evidence for the 2026-07 inventory-delta Phase 4; see the current verification status above.
