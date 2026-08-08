-- =====================================================================
-- V18 — Xac nhan hai lan cho dang ky nhan tin.
--
-- THUAN ADDITIVE, chay lai vo hai.
--
-- V17 luu thang dia chi vao danh sach ngay khi co ai do go vao o o footer. Nghia
-- la bat ky ai cung dang ky ho nguoi khac duoc, va danh sach khong chung minh
-- duoc su dong y — dieu ma Nghi dinh 91/2020 ve chong thu rac doi hoi. Tu day
-- mot dia chi chi vao danh sach sau khi chinh chu bam lien ket trong thu xac thuc.
--
-- Kieu cot theo dung thu Hibernate sinh ra: BIT(1) cho Boolean, DATETIME(6) cho
-- Instant, VARCHAR cho String.
-- =====================================================================

SET @co_da_xac_nhan := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'dang_ky_nhan_tin'
      AND COLUMN_NAME = 'da_xac_nhan'
);
SET @sql_da_xac_nhan := IF(@co_da_xac_nhan = 0,
    'ALTER TABLE `dang_ky_nhan_tin` ADD COLUMN `da_xac_nhan` BIT(1) NOT NULL DEFAULT b''0''',
    'DO 0');
PREPARE stmt_da_xac_nhan FROM @sql_da_xac_nhan;
EXECUTE stmt_da_xac_nhan;
DEALLOCATE PREPARE stmt_da_xac_nhan;

SET @co_ma_xac_nhan := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'dang_ky_nhan_tin'
      AND COLUMN_NAME = 'ma_xac_nhan'
);
-- Cho phep NULL: sau khi xac nhan xong thi khoa duoc xoa di, khong giu lai mot
-- khoa con dung duoc trong database.
SET @sql_ma_xac_nhan := IF(@co_ma_xac_nhan = 0,
    'ALTER TABLE `dang_ky_nhan_tin` ADD COLUMN `ma_xac_nhan` VARCHAR(36) NULL',
    'DO 0');
PREPARE stmt_ma_xac_nhan FROM @sql_ma_xac_nhan;
EXECUTE stmt_ma_xac_nhan;
DEALLOCATE PREPARE stmt_ma_xac_nhan;

SET @co_ngay_xac_nhan := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'dang_ky_nhan_tin'
      AND COLUMN_NAME = 'ngay_xac_nhan'
);
SET @sql_ngay_xac_nhan := IF(@co_ngay_xac_nhan = 0,
    'ALTER TABLE `dang_ky_nhan_tin` ADD COLUMN `ngay_xac_nhan` DATETIME(6) NULL',
    'DO 0');
PREPARE stmt_ngay_xac_nhan FROM @sql_ngay_xac_nhan;
EXECUTE stmt_ngay_xac_nhan;
DEALLOCATE PREPARE stmt_ngay_xac_nhan;

SET @co_khoa_ma_xac_nhan := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'dang_ky_nhan_tin'
      AND INDEX_NAME = 'uq_dang_ky_nhan_tin_ma_xac_nhan'
);
SET @sql_khoa_ma_xac_nhan := IF(@co_khoa_ma_xac_nhan = 0,
    'ALTER TABLE `dang_ky_nhan_tin` ADD UNIQUE KEY `uq_dang_ky_nhan_tin_ma_xac_nhan` (`ma_xac_nhan`)',
    'DO 0');
PREPARE stmt_khoa_ma_xac_nhan FROM @sql_khoa_ma_xac_nhan;
EXECUTE stmt_khoa_ma_xac_nhan;
DEALLOCATE PREPARE stmt_khoa_ma_xac_nhan;

-- Cac dong da co truoc migration nay duoc dang ky duoi luat cu (mot lan), nen coi
-- nhu da xac nhan. Chi chay dung lan them cot: lam lai se bat nguoc trang thai cua
-- nhung dia chi dang cho xac nhan that.
SET @sql_backfill := IF(@co_da_xac_nhan = 0,
    'UPDATE `dang_ky_nhan_tin` SET `da_xac_nhan` = b''1'', `ngay_xac_nhan` = `ngay_dang_ky` WHERE `da_xac_nhan` = b''0''',
    'DO 0');
PREPARE stmt_backfill FROM @sql_backfill;
EXECUTE stmt_backfill;
DEALLOCATE PREPARE stmt_backfill;
