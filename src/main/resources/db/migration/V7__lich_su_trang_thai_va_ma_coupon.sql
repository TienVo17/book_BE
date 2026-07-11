-- Bang lich su chuyen trang thai don hang: moi chuyen hop le ghi 1 dong (ai, luc nao, tu->den).
CREATE TABLE IF NOT EXISTS `lich_su_trang_thai_don_hang` (
    `ma_lich_su` BIGINT NOT NULL AUTO_INCREMENT,
    `ma_don_hang` INT NOT NULL,
    `truong` VARCHAR(20) NOT NULL,                 -- 'GIAO_HANG' | 'THANH_TOAN'
    `tu_gia_tri` INT DEFAULT NULL,
    `den_gia_tri` INT NOT NULL,
    `nguoi_thuc_hien` VARCHAR(255) DEFAULT NULL,    -- ten_dang_nhap tu SecurityContext (khop do rong ten_dang_nhap)
    `thoi_diem` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`ma_lich_su`),
    KEY `idx_lstt_don_hang` (`ma_don_hang`),
    CONSTRAINT `fk_lstt_don_hang` FOREIGN KEY (`ma_don_hang`) REFERENCES `don_hang` (`ma_don_hang`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Lien ket don hang -> coupon da dung (phuc vu hoan luot coupon khi huy don).
-- ON DELETE SET NULL: coupon tung dung trong don van xoa duoc, don chi mat lien ket nguoc.
ALTER TABLE `don_hang` ADD COLUMN `ma_coupon` INT DEFAULT NULL;
ALTER TABLE `don_hang` ADD CONSTRAINT `fk_don_hang_coupon`
    FOREIGN KEY (`ma_coupon`) REFERENCES `coupon` (`ma_coupon`) ON DELETE SET NULL;

-- Cot version cho @Version optimistic lock: chong double-cancel va race voi VNPay callback.
ALTER TABLE `don_hang` ADD COLUMN `version` BIGINT NOT NULL DEFAULT 0;
