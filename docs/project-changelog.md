# Project Changelog

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
