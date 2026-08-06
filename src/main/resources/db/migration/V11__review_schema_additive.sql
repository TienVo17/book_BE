-- =====================================================================
-- V11 — Lop lugc do bo sung cho phan danh gia.
--
-- THUAN ADDITIVE. Moi cot moi deu nullable hoac co DEFAULT, moi bang moi deu
-- tao voi IF NOT EXISTS. Chay lai nhieu lan la vo hai.
--
-- Vi sao gop tat ca vao MOT migration thay vi rai ra tung phase: MySQL auto-commit
-- DDL nen Flyway khong the rollback mot migration hong giua chung; cang it lan cham
-- vao lugc do cang it co hoi ket o trang thai nua voi. Viec nang NOT NULL va
-- DROP COLUMN nam o V12, chay cuoi cung, khi khong con code nao doc cot cu.
--
-- Khuon phong ve information_schema + PREPARE/EXECUTE lay tu V8 va V10.
--
-- MOT NGOAI LE DUY NHAT VOI TINH ADDITIVE — buoc 9 XOA DU LIEU.
-- Rang buoc UNIQUE (ma_nguoi_dung, ma_sach) khong the tao neu con cap trung, nen
-- migration phai chon mot chinh sach thay vi that bai giua chung:
--   * Giu dong co ma_danh_gia LON NHAT cho moi cap (nguoi, sach) — ban moi nhat.
--   * Xoa han moi dong con lai; KHONG co bang luu tru va KHONG khoi phuc duoc.
--   * Binh chon huu ich / hinh anh cua dong bi xoa se di theo qua ON DELETE CASCADE.
-- Tren du lieu hien tai buoc nay la no-op (da kiem: khong ton tai cap trung).
-- Truoc khi chay len mot database khac, dem cap trung bang:
--   SELECT ma_nguoi_dung, ma_sach, COUNT(*) FROM danhgia
--   GROUP BY ma_nguoi_dung, ma_sach HAVING COUNT(*) > 1;
-- Neu ket qua khac rong ma mat du lieu do la khong chap nhan duoc, hay sao luu
-- bang danhgia TRUOC khi migrate.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1) Trang thai kiem duyet tuong minh, backfill tu is_active
-- ---------------------------------------------------------------------
SET @co_trang_thai := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'danhgia' AND COLUMN_NAME = 'trang_thai'
);
SET @sql_trang_thai := IF(@co_trang_thai = 0,
    'ALTER TABLE `danhgia` ADD COLUMN `trang_thai` VARCHAR(20) NOT NULL DEFAULT ''HIEN_THI''',
    'DO 0');
PREPARE stmt_trang_thai FROM @sql_trang_thai;
EXECUTE stmt_trang_thai;
DEALLOCATE PREPARE stmt_trang_thai;

-- is_active NULL duoc coi la DA_AN co chu dich: duong doc cu loc `is_active = 1`,
-- nen dong NULL von da khong hien thi. Map sang HIEN_THI se LO them du lieu ma
-- truoc do khach khong nhin thay.
UPDATE `danhgia` SET `trang_thai` = IF(`is_active` = 1, 'HIEN_THI', 'DA_AN');

-- ---------------------------------------------------------------------
-- 2) Bang chung da mua hang. Van NULL o buoc nay; phase 2 backfill,
--    V12 moi nang len NOT NULL va gan khoa ngoai.
-- ---------------------------------------------------------------------
SET @co_ma_don_hang := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'danhgia' AND COLUMN_NAME = 'ma_don_hang'
);
SET @sql_ma_don_hang := IF(@co_ma_don_hang = 0,
    'ALTER TABLE `danhgia` ADD COLUMN `ma_don_hang` INT DEFAULT NULL',
    'DO 0');
PREPARE stmt_ma_don_hang FROM @sql_ma_don_hang;
EXECUTE stmt_ma_don_hang;
DEALLOCATE PREPARE stmt_ma_don_hang;

-- ---------------------------------------------------------------------
-- 3) Phan hoi cua shop — moi danh gia toi da mot phan hoi, nen luu thang
--    tren dong danh gia thay vi mot bang rieng.
-- ---------------------------------------------------------------------
SET @co_phan_hoi := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'danhgia' AND COLUMN_NAME = 'phan_hoi_shop'
);
SET @sql_phan_hoi := IF(@co_phan_hoi = 0,
    'ALTER TABLE `danhgia`
        ADD COLUMN `phan_hoi_shop` TEXT DEFAULT NULL,
        ADD COLUMN `phan_hoi_shop_tai` DATETIME(6) DEFAULT NULL,
        ADD COLUMN `phan_hoi_shop_boi` INT DEFAULT NULL',
    'DO 0');
PREPARE stmt_phan_hoi FROM @sql_phan_hoi;
EXECUTE stmt_phan_hoi;
DEALLOCATE PREPARE stmt_phan_hoi;

-- ---------------------------------------------------------------------
-- 4) Chong tai sinh danh gia da bi an: chu so huu xoa roi dang lai se lam
--    unique (nguoi, sach) trong lai. Cot nay ghi nho rang cap do TUNG bi an.
-- ---------------------------------------------------------------------
SET @co_tung_bi_an := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'danhgia' AND COLUMN_NAME = 'tung_bi_an'
);
SET @sql_tung_bi_an := IF(@co_tung_bi_an = 0,
    'ALTER TABLE `danhgia` ADD COLUMN `tung_bi_an` TINYINT(1) NOT NULL DEFAULT 0',
    'DO 0');
PREPARE stmt_tung_bi_an FROM @sql_tung_bi_an;
EXECUTE stmt_tung_bi_an;
DEALLOCATE PREPARE stmt_tung_bi_an;

-- Chi `is_active = 0` moi la bang chung admin da an that. Neu keo theo ca
-- `trang_thai = 'DA_AN'` thi dong co `is_active` NULL — von chua ai an, chi la
-- du lieu thieu — se bi ghi vinh vien la "tung bi an" va mat quyen dang lai.
UPDATE `danhgia` SET `tung_bi_an` = 1 WHERE `is_active` = 0;

-- ---------------------------------------------------------------------
-- 5) So luot danh gia ghi san tren sach.
--    Di kem cot trung_binh_xep_hang von da ton tai va dang thieu writer.
-- ---------------------------------------------------------------------
SET @co_so_luot := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sach' AND COLUMN_NAME = 'so_luot_danh_gia'
);
SET @sql_so_luot := IF(@co_so_luot = 0,
    'ALTER TABLE `sach` ADD COLUMN `so_luot_danh_gia` INT NOT NULL DEFAULT 0',
    'DO 0');
PREPARE stmt_so_luot FROM @sql_so_luot;
EXECUTE stmt_so_luot;
DEALLOCATE PREPARE stmt_so_luot;

-- ---------------------------------------------------------------------
-- 6) Duong lui cho buoc backfill ben duoi.
--    Backfill ghi de trung_binh_xep_hang tai cho; khong luu lai thi khong co
--    cach nao quay ve gia tri seed.
-- ---------------------------------------------------------------------
SET @co_backup_diem := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sach' AND COLUMN_NAME = 'trung_binh_xep_hang_truoc_v11'
);
SET @sql_backup_diem := IF(@co_backup_diem = 0,
    'ALTER TABLE `sach` ADD COLUMN `trung_binh_xep_hang_truoc_v11` DOUBLE DEFAULT NULL',
    'DO 0');
PREPARE stmt_backup_diem FROM @sql_backup_diem;
EXECUTE stmt_backup_diem;
DEALLOCATE PREPARE stmt_backup_diem;

-- Chup ban luu DUY NHAT o lan tao cot. Loc `... IS NULL` khong dung lam dieu kien
-- chay lai: sach co trung_binh_xep_hang NULL se de lai ban luu NULL, roi lan chay
-- thu hai chep de bang chinh gia tri buoc 10 vua tinh — pha huy duong lui.
SET @sql_backup_luu := IF(@co_backup_diem = 0,
    'UPDATE `sach` SET `trung_binh_xep_hang_truoc_v11` = `trung_binh_xep_hang`',
    'DO 0');
PREPARE stmt_backup_luu FROM @sql_backup_luu;
EXECUTE stmt_backup_luu;
DEALLOCATE PREPARE stmt_backup_luu;

-- ---------------------------------------------------------------------
-- 7) Bang binh chon huu ich. Cascade de xoa danh gia keo theo binh chon.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `danhgia_huu_ich` (
    `ma_huu_ich`    BIGINT NOT NULL AUTO_INCREMENT,
    `ma_danh_gia`   BIGINT NOT NULL,
    `ma_nguoi_dung` INT NOT NULL,
    `tao_luc`       DATETIME(6) NOT NULL,
    PRIMARY KEY (`ma_huu_ich`),
    UNIQUE KEY `uk_danhgia_huu_ich_nguoi` (`ma_danh_gia`, `ma_nguoi_dung`),
    CONSTRAINT `fk_huu_ich_danh_gia` FOREIGN KEY (`ma_danh_gia`)
        REFERENCES `danhgia` (`ma_danh_gia`) ON DELETE CASCADE,
    CONSTRAINT `fk_huu_ich_nguoi_dung` FOREIGN KEY (`ma_nguoi_dung`)
        REFERENCES `nguoi_dung` (`ma_nguoi_dung`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 8) Bang anh dinh kem.
--    Cascade o day chi don DONG DB. Anh tren Cloudinary phai duoc xoa tuong minh
--    trong deleteReview truoc khi dong bi xoa — neu khong, cloudinary_public_id
--    bien mat cung luc voi anh bi bo roi va khong con cach nao truy ra de don.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `danhgia_hinh_anh` (
    `ma_hinh_anh`           BIGINT NOT NULL AUTO_INCREMENT,
    `ma_danh_gia`           BIGINT NOT NULL,
    `url_hinh`              VARCHAR(500) NOT NULL,
    `cloudinary_public_id`  VARCHAR(255) DEFAULT NULL,
    `thu_tu`                INT NOT NULL DEFAULT 0,
    `tao_luc`               DATETIME(6) NOT NULL,
    PRIMARY KEY (`ma_hinh_anh`),
    KEY `idx_danhgia_hinh_anh_danh_gia` (`ma_danh_gia`),
    CONSTRAINT `fk_hinh_anh_danh_gia` FOREIGN KEY (`ma_danh_gia`)
        REFERENCES `danhgia` (`ma_danh_gia`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 9) Moi nguoi mot danh gia moi cuon sach.
--    Dedupe truoc: giu ban moi nhat, bo phan con lai. Chay tren du lieu hien tai
--    la no-op (da do: khong co cap trung), nhung migration phai tu bao ve khi
--    chay tren database khac.
-- ---------------------------------------------------------------------
DELETE d FROM `danhgia` d
INNER JOIN `danhgia` giu
    ON  d.`ma_nguoi_dung` = giu.`ma_nguoi_dung`
    AND d.`ma_sach`       = giu.`ma_sach`
    AND d.`ma_danh_gia`   < giu.`ma_danh_gia`;

SET @co_uk_nguoi_sach := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'danhgia'
      AND INDEX_NAME = 'uk_danhgia_nguoi_sach'
);
SET @sql_uk_nguoi_sach := IF(@co_uk_nguoi_sach = 0,
    'ALTER TABLE `danhgia` ADD CONSTRAINT `uk_danhgia_nguoi_sach` UNIQUE (`ma_nguoi_dung`, `ma_sach`)',
    'DO 0');
PREPARE stmt_uk_nguoi_sach FROM @sql_uk_nguoi_sach;
EXECUTE stmt_uk_nguoi_sach;
DEALLOCATE PREPARE stmt_uk_nguoi_sach;

-- ---------------------------------------------------------------------
-- 10) Backfill du lieu tong hop tu du lieu that.
--     Truoc buoc nay, trung_binh_xep_hang chi la so seed tinh — khong dong code
--     nao trong toan bo codebase tung ghi vao no.
-- ---------------------------------------------------------------------
UPDATE `sach` s SET
    s.`trung_binh_xep_hang` = COALESCE((
        SELECT AVG(d.`diem_xep_hang`) FROM `danhgia` d
        WHERE d.`ma_sach` = s.`ma_sach` AND d.`trang_thai` = 'HIEN_THI'
    ), 0),
    s.`so_luot_danh_gia` = (
        SELECT COUNT(*) FROM `danhgia` d
        WHERE d.`ma_sach` = s.`ma_sach` AND d.`trang_thai` = 'HIEN_THI'
    );
