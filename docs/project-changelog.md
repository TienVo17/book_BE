# Project Changelog

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
