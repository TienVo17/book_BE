-- =====================================================================
-- V14 — Idempotency cho upload anh danh gia.
--
-- THUAN ADDITIVE, chay lai vo hai sau mot lan DDL MySQL auto-commit bi ngat.
-- Client giu nguyen Idempotency-Key cho tung tep khi retry. Unique key theo
-- (ma_danh_gia, idempotency_key) chan hai dong cho cung mot y dinh upload;
-- noi_dung_sha256 chan viec tai noi dung khac bang khoa da dung.
-- =====================================================================

SET @co_idempotency_key := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'danhgia_hinh_anh'
      AND COLUMN_NAME = 'idempotency_key'
);
SET @sql_idempotency_key := IF(@co_idempotency_key = 0,
    'ALTER TABLE `danhgia_hinh_anh` ADD COLUMN `idempotency_key` VARCHAR(100) NULL',
    'DO 0');
PREPARE stmt_idempotency_key FROM @sql_idempotency_key;
EXECUTE stmt_idempotency_key;
DEALLOCATE PREPARE stmt_idempotency_key;

SET @co_noi_dung_sha256 := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'danhgia_hinh_anh'
      AND COLUMN_NAME = 'noi_dung_sha256'
);
SET @sql_noi_dung_sha256 := IF(@co_noi_dung_sha256 = 0,
    'ALTER TABLE `danhgia_hinh_anh` ADD COLUMN `noi_dung_sha256` CHAR(64) NULL',
    'DO 0');
PREPARE stmt_noi_dung_sha256 FROM @sql_noi_dung_sha256;
EXECUTE stmt_noi_dung_sha256;
DEALLOCATE PREPARE stmt_noi_dung_sha256;

-- Anh ton tai truoc migration khong co request key/hash. Gan gia tri on dinh,
-- duy nhat theo id de co the nang hai cot len NOT NULL ma khong suy dien noi dung.
UPDATE `danhgia_hinh_anh`
SET `idempotency_key` = CONCAT('legacy-', `ma_hinh_anh`)
WHERE `idempotency_key` IS NULL OR `idempotency_key` = '';

UPDATE `danhgia_hinh_anh`
SET `noi_dung_sha256` = REPEAT('0', 64)
WHERE `noi_dung_sha256` IS NULL OR `noi_dung_sha256` = '';

SET @idempotency_key_nullable := (
    SELECT IS_NULLABLE FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'danhgia_hinh_anh'
      AND COLUMN_NAME = 'idempotency_key'
);
SET @sql_idempotency_not_null := IF(@idempotency_key_nullable = 'YES',
    'ALTER TABLE `danhgia_hinh_anh` MODIFY COLUMN `idempotency_key` VARCHAR(100) NOT NULL',
    'DO 0');
PREPARE stmt_idempotency_not_null FROM @sql_idempotency_not_null;
EXECUTE stmt_idempotency_not_null;
DEALLOCATE PREPARE stmt_idempotency_not_null;

SET @noi_dung_sha256_nullable := (
    SELECT IS_NULLABLE FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'danhgia_hinh_anh'
      AND COLUMN_NAME = 'noi_dung_sha256'
);
SET @sql_sha_not_null := IF(@noi_dung_sha256_nullable = 'YES',
    'ALTER TABLE `danhgia_hinh_anh` MODIFY COLUMN `noi_dung_sha256` CHAR(64) NOT NULL',
    'DO 0');
PREPARE stmt_sha_not_null FROM @sql_sha_not_null;
EXECUTE stmt_sha_not_null;
DEALLOCATE PREPARE stmt_sha_not_null;

SET @co_uk_review_image_idempotency := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'danhgia_hinh_anh'
      AND INDEX_NAME = 'uk_review_image_idempotency'
);
SET @sql_uk_review_image_idempotency := IF(@co_uk_review_image_idempotency = 0,
    'ALTER TABLE `danhgia_hinh_anh` ADD CONSTRAINT `uk_review_image_idempotency` UNIQUE (`ma_danh_gia`, `idempotency_key`)',
    'DO 0');
PREPARE stmt_uk_review_image_idempotency FROM @sql_uk_review_image_idempotency;
EXECUTE stmt_uk_review_image_idempotency;
DEALLOCATE PREPARE stmt_uk_review_image_idempotency;
