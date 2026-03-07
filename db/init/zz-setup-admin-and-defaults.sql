-- Chay sau web_ban_sach.sql
-- Them cot bo sung cho nguoi_dung va sach
-- Dung PROCEDURE de tuong thich MySQL 8.0

DELIMITER //

CREATE PROCEDURE setup_columns()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='nguoi_dung' AND COLUMN_NAME='da_kich_hoat') THEN
        ALTER TABLE `nguoi_dung` ADD COLUMN `da_kich_hoat` TINYINT(1) NOT NULL DEFAULT 1;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='nguoi_dung' AND COLUMN_NAME='ma_kich_hoat') THEN
        ALTER TABLE `nguoi_dung` ADD COLUMN `ma_kich_hoat` VARCHAR(255) DEFAULT NULL;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sach' AND COLUMN_NAME='is_active') THEN
        ALTER TABLE `sach` ADD COLUMN `is_active` INT DEFAULT 1;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sach' AND COLUMN_NAME='mo_ta_ngan') THEN
        ALTER TABLE `sach` ADD COLUMN `mo_ta_ngan` TEXT NULL AFTER `mo_ta`;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sach' AND COLUMN_NAME='mo_ta_chi_tiet') THEN
        ALTER TABLE `sach` ADD COLUMN `mo_ta_chi_tiet` LONGTEXT NULL AFTER `mo_ta_ngan`;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sach' AND COLUMN_NAME='created_at') THEN
        ALTER TABLE `sach` ADD COLUMN `created_at` DATETIME NULL;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sach' AND COLUMN_NAME='updated_at') THEN
        ALTER TABLE `sach` ADD COLUMN `updated_at` DATETIME NULL;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sach' AND COLUMN_NAME='slug') THEN
        ALTER TABLE `sach` ADD COLUMN `slug` VARCHAR(512) NULL;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sach' AND INDEX_NAME='uk_sach_slug') THEN
        ALTER TABLE `sach` ADD UNIQUE INDEX `uk_sach_slug` (`slug`);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sach_thong_tin_chi_tiet') THEN
        CREATE TABLE `sach_thong_tin_chi_tiet` (
            `ma_sach` INT NOT NULL,
            `cong_ty_phat_hanh` VARCHAR(255) NULL,
            `nha_xuat_ban` VARCHAR(255) NULL,
            `ngay_xuat_ban` DATE NULL,
            `so_trang` INT NULL,
            `loai_bia` VARCHAR(100) NULL,
            `ngon_ngu` VARCHAR(100) NULL,
            `kich_thuoc` VARCHAR(100) NULL,
            `trong_luong_gram` INT NULL,
            `phien_ban` VARCHAR(100) NULL,
            PRIMARY KEY (`ma_sach`),
            CONSTRAINT `fk_sach_thong_tin_chi_tiet_sach`
                FOREIGN KEY (`ma_sach`) REFERENCES `sach` (`ma_sach`) ON DELETE CASCADE
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
    END IF;
END //

DELIMITER ;

CALL setup_columns();
DROP PROCEDURE IF EXISTS setup_columns;

UPDATE `nguoi_dung` SET `da_kich_hoat` = 1 WHERE `da_kich_hoat` = 0;
UPDATE `sach` SET `is_active` = 1 WHERE `is_active` IS NULL;
UPDATE `sach` SET `mo_ta_chi_tiet` = `mo_ta` WHERE (`mo_ta_chi_tiet` IS NULL OR `mo_ta_chi_tiet` = '') AND `mo_ta` IS NOT NULL;
UPDATE `sach`
SET `mo_ta_ngan` = LEFT(REPLACE(REPLACE(REPLACE(`mo_ta_chi_tiet`, '<br>', ' '), '<br/>', ' '), '<br />', ' '), 280)
WHERE (`mo_ta_ngan` IS NULL OR `mo_ta_ngan` = '') AND `mo_ta_chi_tiet` IS NOT NULL;

DELETE FROM `nguoidung_quyen` WHERE `ma_quyen` > 3;
DELETE FROM `quyen` WHERE `ma_quyen` > 3;

INSERT IGNORE INTO `nguoidung_quyen` (`ma_quyen`, `ma_nguoi_dung`) VALUES (1, 1);
INSERT IGNORE INTO `nguoidung_quyen` (`ma_quyen`, `ma_nguoi_dung`) VALUES (3, 1);

INSERT INTO `nguoi_dung` (`ho_dem`, `ten`, `ten_dang_nhap`, `mat_khau`, `gioi_tinh`, `email`, `so_dien_thoai`, `dia_chi_mua_hang`, `dia_chi_giao_hang`, `da_kich_hoat`)
VALUES ('Admin', 'System', 'admin', '$2a$10$B6qPwSi5FHcaX4a34FwqRuMnBGl1wz5V30js1OQ3Nm1/CCoWDkiv2', 'M', 'admin@bookstore.com', '0000000000', '', '', 1);

SET @admin_id = LAST_INSERT_ID();
INSERT INTO `nguoidung_quyen` (`ma_quyen`, `ma_nguoi_dung`) VALUES (1, @admin_id);
INSERT INTO `nguoidung_quyen` (`ma_quyen`, `ma_nguoi_dung`) VALUES (3, @admin_id);
