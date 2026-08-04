-- =============================================
-- V10: Vo hieu hoa cac tai khoan seed tu V3/V4, them rang buoc dinh danh
--      va bang trang thai bootstrap admin mot lan.
--
-- Ghi chu quan trong: comment trong V3/V4 ghi "Password: 1" nhung hash thuc te KHONG
-- khop voi "1" (da kiem chung bang BCrypt.checkpw). Day khong phai credential cong khai
-- bi lo. Van de la 6 tai khoan nay dang kich hoat tren production voi mat khau khong ai
-- biet, trong do 'admin' nam quyen ADMIN — tai khoan mo coi khong the kiem soat.
--
-- V3/V4 KHONG duoc sua: chung da apply tren database dang chay. Migration forward-only
-- nay chay ngay sau chung trong cung chuoi Flyway, nen ca database moi lan database cu
-- deu ket thuc o trang thai khong con tai khoan seed dung duoc.
-- =============================================

-- ---------------------------------------------
-- 1) Thu hoi quyen cua dung cac dinh danh seed da biet
--    Chi khop khi CA username + email + hash seed goc con nguyen ven, de khong
--    dung cham tai khoan that da doi mat khau hoac tai khoan trung ID.
-- ---------------------------------------------
DELETE nq FROM `nguoidung_quyen` nq
JOIN `nguoi_dung` nd ON nd.`ma_nguoi_dung` = nq.`ma_nguoi_dung`
WHERE nd.`mat_khau` = '$2a$10$B6qPwSi5FHcaX4a34FwqRuMnBGl1wz5V30js1OQ3Nm1/CCoWDkiv2'
  AND (
    (nd.`ten_dang_nhap` = 'admin' AND nd.`email` = 'admin@bookstore.com')
    OR (nd.`ten_dang_nhap` = 'user1' AND nd.`email` = 'nguoidung1@email.com')
    OR (nd.`ten_dang_nhap` = 'user2' AND nd.`email` = 'nguoidung2@email.com')
    OR (nd.`ten_dang_nhap` = 'user3' AND nd.`email` = 'nguoidung3@email.com')
    OR (nd.`ten_dang_nhap` = 'user4' AND nd.`email` = 'nguoidung4@email.com')
    OR (nd.`ten_dang_nhap` = 'user5' AND nd.`email` = 'nguoidung5@email.com')
  );

-- ---------------------------------------------
-- 2) Vo hieu hoa dang nhap cua chinh cac dinh danh do
--    Khong xoa: don hang/danh gia demo tham chieu toi ho qua khoa ngoai.
--    Gia tri mat khau moi khong phai BCrypt hash nen khong the match bat ky input nao.
-- ---------------------------------------------
UPDATE `nguoi_dung`
SET `mat_khau` = CONCAT('!disabled-seed-', UUID()),
    `da_kich_hoat` = 0,
    `ma_kich_hoat` = NULL,
    `reset_password_token` = NULL,
    `reset_password_token_expiry` = NULL
WHERE `mat_khau` = '$2a$10$B6qPwSi5FHcaX4a34FwqRuMnBGl1wz5V30js1OQ3Nm1/CCoWDkiv2'
  AND (
    (`ten_dang_nhap` = 'admin' AND `email` = 'admin@bookstore.com')
    OR (`ten_dang_nhap` = 'user1' AND `email` = 'nguoidung1@email.com')
    OR (`ten_dang_nhap` = 'user2' AND `email` = 'nguoidung2@email.com')
    OR (`ten_dang_nhap` = 'user3' AND `email` = 'nguoidung3@email.com')
    OR (`ten_dang_nhap` = 'user4' AND `email` = 'nguoidung4@email.com')
    OR (`ten_dang_nhap` = 'user5' AND `email` = 'nguoidung5@email.com')
  );

-- ---------------------------------------------
-- 3) Rang buoc dinh danh duy nhat
--    Idempotent qua information_schema (giong V8). Neu database dang chua username/email
--    trung nhau, ALTER TABLE se that bai va Flyway dung lai — day la fail-closed co chu y,
--    du lieu trung phai duoc nguoi van hanh xu ly thu cong truoc khi migrate tiep.
-- ---------------------------------------------
SET @co_uk_ten_dang_nhap := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'nguoi_dung'
      AND INDEX_NAME = 'uk_nguoi_dung_ten_dang_nhap'
);
SET @sql_uk_ten_dang_nhap := IF(@co_uk_ten_dang_nhap = 0,
    'ALTER TABLE `nguoi_dung` ADD CONSTRAINT `uk_nguoi_dung_ten_dang_nhap` UNIQUE (`ten_dang_nhap`)',
    'DO 0');
PREPARE stmt_uk_ten_dang_nhap FROM @sql_uk_ten_dang_nhap;
EXECUTE stmt_uk_ten_dang_nhap;
DEALLOCATE PREPARE stmt_uk_ten_dang_nhap;

SET @co_uk_email := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'nguoi_dung'
      AND INDEX_NAME = 'uk_nguoi_dung_email'
);
SET @sql_uk_email := IF(@co_uk_email = 0,
    'ALTER TABLE `nguoi_dung` ADD CONSTRAINT `uk_nguoi_dung_email` UNIQUE (`email`)',
    'DO 0');
PREPARE stmt_uk_email FROM @sql_uk_email;
EXECUTE stmt_uk_email;
DEALLOCATE PREPARE stmt_uk_email;

-- ---------------------------------------------
-- 4) Trang thai bootstrap admin — dung 1 dong duy nhat (singleton)
--    Primary key + CHECK giu bang luon chi co dung mot dong, nen hai instance khoi dong
--    dong thoi khong the tao ra hai admin: ca hai cung tranh khoa tren chinh dong nay.
-- ---------------------------------------------
CREATE TABLE IF NOT EXISTS `admin_bootstrap_state` (
    `singleton_id` TINYINT NOT NULL,
    `da_su_dung` TINYINT(1) NOT NULL DEFAULT 0,
    `thoi_diem_su_dung` DATETIME(6) DEFAULT NULL,
    `ten_dang_nhap_da_tao` VARCHAR(255) DEFAULT NULL,
    PRIMARY KEY (`singleton_id`),
    CONSTRAINT `chk_admin_bootstrap_singleton` CHECK (`singleton_id` = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Database da co admin hoat dong thi coi nhu bootstrap da duoc su dung: khong mo them
-- duong tao admin moi tren mot he thong dang van hanh.
INSERT INTO `admin_bootstrap_state` (`singleton_id`, `da_su_dung`)
SELECT 1, CASE WHEN EXISTS (
    SELECT 1 FROM `nguoi_dung` nd
    JOIN `nguoidung_quyen` nq ON nq.`ma_nguoi_dung` = nd.`ma_nguoi_dung`
    JOIN `quyen` q ON q.`ma_quyen` = nq.`ma_quyen`
    WHERE q.`ten_quyen` = 'ADMIN' AND nd.`da_kich_hoat` = 1
) THEN 1 ELSE 0 END
ON DUPLICATE KEY UPDATE `singleton_id` = `singleton_id`;
