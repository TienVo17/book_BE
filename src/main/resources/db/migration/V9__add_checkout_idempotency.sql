-- Checkout idempotency: client sends an optional Idempotency-Key header on
-- POST /api/don-hang/them. We persist the key + a fingerprint of the normalized
-- request on the resulting order so a retried request (same user + same key)
-- can be detected and safely replayed instead of creating a duplicate order.
--
-- MySQL/InnoDB treats NULL as distinct in a UNIQUE index, so any number of
-- legacy/no-key orders (checkout_idempotency_key = NULL) remain unaffected by
-- this constraint; it only rejects a second INSERT for the same
-- (ma_nguoi_dung, checkout_idempotency_key) pair when the key is non-NULL.
ALTER TABLE `don_hang`
    ADD COLUMN `checkout_idempotency_key` VARCHAR(100) DEFAULT NULL,
    ADD COLUMN `checkout_request_fingerprint` VARCHAR(128) DEFAULT NULL,
    ADD COLUMN `checkout_response_coupon_code` VARCHAR(255) DEFAULT NULL,
    ADD COLUMN `checkout_response_payment_method` VARCHAR(20) DEFAULT NULL,
    ADD COLUMN `checkout_response_payment_status` INT DEFAULT NULL;

ALTER TABLE `don_hang`
    ADD CONSTRAINT `uk_don_hang_nguoi_dung_idempotency_key`
        UNIQUE (`ma_nguoi_dung`, `checkout_idempotency_key`);
