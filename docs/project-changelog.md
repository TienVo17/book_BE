# Project Changelog

## 2026-07-08

### Documentation sync pass
- Reconciled all documentation files against current codebase state.
- Fixed JWT expiration time: corrected from "30 phút" to accurate "8 giờ default" (configurable via `JWT_EXPIRATION_MS`).
- Added comprehensive "Known Limitations" sections to project-overview-pdr.md, codebase-summary.md, code-standards.md, and system-architecture.md documenting:
  - Authorization matcher inconsistency (public GET endpoints for `/api/admin/*` paths)
  - Unprotected admin mutation endpoints in SachUserController
  - CORS configuration conflicts between SecurityConfiguration and RestConfig
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

### Runtime and Docker verification
- Verified backend Docker build passes with Maven inside container.
- Verified frontend Docker build passes from canonical repo `E:/BT/book_FE` via compose context `../book_FE`.
- Verified `docker compose up -d` succeeds for mysql, backend, and frontend services.
