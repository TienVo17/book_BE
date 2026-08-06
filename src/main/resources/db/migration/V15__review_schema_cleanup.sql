-- =====================================================================
-- V15 — Don dep luoc do danh gia sau khi code chi con dung trang_thai.
--
-- HAI CONG BAT BUOC truoc DDL:
--   1. Khong con runtime review-domain nao doc/ghi is_active.
--   2. SELECT COUNT(*) FROM danhgia WHERE ma_don_hang IS NULL phai bang 0.
--
-- V12 da backfill moi review cu bang don DA_GIAO that hoac don demo duoc loai
-- khoi thong ke. V15 khong tu suy dien, xoa hay gan lai du lieu; neu cong so 2
-- khong dat, migration dung truoc ALTER dau tien.
--
-- MySQL auto-commit DDL. Moi ALTER deu co information_schema guard de script
-- chay lai an toan sau mot lan deploy bi ngat giua chung.
-- =====================================================================

SET @review_order_null_count := (
    SELECT COUNT(*) FROM `danhgia` WHERE `ma_don_hang` IS NULL
);
SET @assert_review_order_gate := IF(
    @review_order_null_count = 0,
    'DO 0',
    'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''V15 blocked: danhgia.ma_don_hang still contains NULL'''
);
PREPARE stmt_review_order_gate FROM @assert_review_order_gate;
EXECUTE stmt_review_order_gate;
DEALLOCATE PREPARE stmt_review_order_gate;

SET @ma_don_hang_nullable := (
    SELECT `IS_NULLABLE` FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'danhgia'
      AND COLUMN_NAME = 'ma_don_hang'
);
SET @sql_ma_don_hang_not_null := IF(
    @ma_don_hang_nullable = 'YES',
    'ALTER TABLE `danhgia` MODIFY COLUMN `ma_don_hang` INT NOT NULL',
    'DO 0'
);
PREPARE stmt_ma_don_hang_not_null FROM @sql_ma_don_hang_not_null;
EXECUTE stmt_ma_don_hang_not_null;
DEALLOCATE PREPARE stmt_ma_don_hang_not_null;

SET @co_fk_danhgia_don_hang := (
    SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'danhgia'
      AND COLUMN_NAME = 'ma_don_hang'
      AND REFERENCED_TABLE_NAME = 'don_hang'
      AND REFERENCED_COLUMN_NAME = 'ma_don_hang'
);
SET @sql_fk_danhgia_don_hang := IF(
    @co_fk_danhgia_don_hang = 0,
    'ALTER TABLE `danhgia` ADD CONSTRAINT `fk_danhgia_don_hang` FOREIGN KEY (`ma_don_hang`) REFERENCES `don_hang` (`ma_don_hang`)',
    'DO 0'
);
PREPARE stmt_fk_danhgia_don_hang FROM @sql_fk_danhgia_don_hang;
EXECUTE stmt_fk_danhgia_don_hang;
DEALLOCATE PREPARE stmt_fk_danhgia_don_hang;

SET @co_review_is_active := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'danhgia'
      AND COLUMN_NAME = 'is_active'
);
SET @sql_drop_review_is_active := IF(
    @co_review_is_active = 1,
    'ALTER TABLE `danhgia` DROP COLUMN `is_active`',
    'DO 0'
);
PREPARE stmt_drop_review_is_active FROM @sql_drop_review_is_active;
EXECUTE stmt_drop_review_is_active;
DEALLOCATE PREPARE stmt_drop_review_is_active;
