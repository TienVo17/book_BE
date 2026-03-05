-- Chạy sau web_ban_sach.sql (theo thứ tự alphabet)
-- Thêm cột da_kich_hoat, is_active và set mặc định cho dữ liệu cũ
-- Dùng PROCEDURE để tương thích MySQL 8.0 (không hỗ trợ ADD COLUMN IF NOT EXISTS)

DELIMITER //

CREATE PROCEDURE setup_columns()
BEGIN
    -- Thêm cột da_kich_hoat cho nguoi_dung
    IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='nguoi_dung' AND COLUMN_NAME='da_kich_hoat') THEN
        ALTER TABLE `nguoi_dung` ADD COLUMN `da_kich_hoat` TINYINT(1) NOT NULL DEFAULT 1;
    END IF;

    -- Thêm cột ma_kich_hoat cho nguoi_dung
    IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='nguoi_dung' AND COLUMN_NAME='ma_kich_hoat') THEN
        ALTER TABLE `nguoi_dung` ADD COLUMN `ma_kich_hoat` VARCHAR(255) DEFAULT NULL;
    END IF;

    -- Thêm cột is_active cho sach
    IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sach' AND COLUMN_NAME='is_active') THEN
        ALTER TABLE `sach` ADD COLUMN `is_active` INT DEFAULT 1;
    END IF;
END //

DELIMITER ;

CALL setup_columns();
DROP PROCEDURE IF EXISTS setup_columns;

-- Kích hoạt tất cả tài khoản có sẵn
UPDATE `nguoi_dung` SET `da_kich_hoat` = 1 WHERE `da_kich_hoat` = 0;

-- Active tất cả sách có sẵn
UPDATE `sach` SET `is_active` = 1 WHERE `is_active` IS NULL;

-- Xóa quyền trùng lặp trong bảng quyen (giữ lại ma_quyen 1,2,3)
DELETE FROM `nguoidung_quyen` WHERE `ma_quyen` > 3;
DELETE FROM `quyen` WHERE `ma_quyen` > 3;

-- Đảm bảo user1 có quyền ADMIN (ma_quyen=1)
INSERT IGNORE INTO `nguoidung_quyen` (`ma_quyen`, `ma_nguoi_dung`) VALUES (1, 1);

-- Đảm bảo user1 cũng có quyền USER để đăng nhập bình thường
INSERT IGNORE INTO `nguoidung_quyen` (`ma_quyen`, `ma_nguoi_dung`) VALUES (3, 1);

-- ===== TẠO TÀI KHOẢN ADMIN MỚI =====
-- Username: admin / Password: admin123@
INSERT INTO `nguoi_dung` (`ho_dem`, `ten`, `ten_dang_nhap`, `mat_khau`, `gioi_tinh`, `email`, `so_dien_thoai`, `dia_chi_mua_hang`, `dia_chi_giao_hang`, `da_kich_hoat`)
VALUES ('Admin', 'System', 'admin', '$2a$10$B6qPwSi5FHcaX4a34FwqRuMnBGl1wz5V30js1OQ3Nm1/CCoWDkiv2', 'M', 'admin@bookstore.com', '0000000000', '', '', 1);

-- Gán quyền ADMIN + USER cho tài khoản admin mới
SET @admin_id = LAST_INSERT_ID();
INSERT INTO `nguoidung_quyen` (`ma_quyen`, `ma_nguoi_dung`) VALUES (1, @admin_id);
INSERT INTO `nguoidung_quyen` (`ma_quyen`, `ma_nguoi_dung`) VALUES (3, @admin_id);
