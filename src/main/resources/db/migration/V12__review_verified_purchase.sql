-- =====================================================================
-- V12 — Xac minh da mua hang cho danh gia.
--
-- Ba viec: co danh dau don demo, bang tombstone chong dang lai, va backfill
-- danhgia.ma_don_hang (cot da co tu V11, dang NULL toan bo).
--
-- THUAN ADDITIVE + DML. Khong DROP, khong doi kieu cot nao. Chay lai vo hai:
-- moi ALTER boc trong kiem tra information_schema, moi buoc backfill dieu kien
-- tren `ma_don_hang IS NULL` nen lan chay thu hai khong con dong nao de sua.
--
-- Cap phat so: da chay `ls db/migration | sort -V | tail -1` truoc khi tao file
-- nay, ket qua V11. Ban ke hoach du kien phase 7 dung V12; phase 7 lui xuong
-- V13 vi `spring.flyway.out-of-order` mac dinh false — mot migration so thap
-- hon moc da ap se bi tu choi.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1) Don demo do migration tao ra khong duoc tinh vao thong ke.
--
--    `don_hang` khong phai bang tro: DonHangRepository.sumDoanhThu va
--    ChiTietDonHangRepository.findTopBanChay doc thang no va do vao dashboard
--    admin. Seed don ma khong danh dau thi doanh thu bao cao la so ao — dung
--    loai du lieu noi doi ma ca ke hoach nay sinh ra de sua.
-- ---------------------------------------------------------------------
SET @co_la_don_demo := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'don_hang' AND COLUMN_NAME = 'la_don_demo'
);
SET @sql_la_don_demo := IF(@co_la_don_demo = 0,
    'ALTER TABLE `don_hang` ADD COLUMN `la_don_demo` TINYINT(1) NOT NULL DEFAULT 0',
    'DO 0');
PREPARE stmt_la_don_demo FROM @sql_la_don_demo;
EXECUTE stmt_la_don_demo;
DEALLOCATE PREPARE stmt_la_don_demo;

-- ---------------------------------------------------------------------
-- 2) Chi mot truy van cho moi lan mo trang san pham: don da giao cua chinh
--    nguoi dung, chua sach dang xem.
-- ---------------------------------------------------------------------
SET @co_idx_nguoi_trang_thai := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'don_hang'
      AND INDEX_NAME = 'idx_don_hang_nguoi_trang_thai'
);
SET @sql_idx_nguoi_trang_thai := IF(@co_idx_nguoi_trang_thai = 0,
    'ALTER TABLE `don_hang` ADD INDEX `idx_don_hang_nguoi_trang_thai` (`ma_nguoi_dung`, `trang_thai_giao_hang`)',
    'DO 0');
PREPARE stmt_idx_nguoi_trang_thai FROM @sql_idx_nguoi_trang_thai;
EXECUTE stmt_idx_nguoi_trang_thai;
DEALLOCATE PREPARE stmt_idx_nguoi_trang_thai;

-- ---------------------------------------------------------------------
-- 3) Tombstone: cap (nguoi, sach) da tung bi admin an.
--
--    Co `danhgia.tung_bi_an` khong du cho viec nay: no chet cung dong danh gia
--    khi chu so huu tu xoa. Ma do dung la duong lach — admin an, tac gia xoa
--    (duoc phep, va dung), unique (nguoi, sach) duoc giai phong, don DA_GIAO
--    van con nen dieu kien lai thoa, dang lai, lap vo han.
--
--    KHONG cascade theo `danhgia`: tombstone phai song sot khi dong danh gia
--    bi xoa, do la toan bo ly do no ton tai. Van cascade theo nguoi dung va
--    sach vi khi hai thuc the do bien mat thi cap (nguoi, sach) het nghia.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `danhgia_an_tombstone` (
    `ma_tombstone`  BIGINT NOT NULL AUTO_INCREMENT,
    `ma_nguoi_dung` INT NOT NULL,
    `ma_sach`       INT NOT NULL,
    `tao_luc`       DATETIME(6) NOT NULL,
    PRIMARY KEY (`ma_tombstone`),
    UNIQUE KEY `uk_tombstone_nguoi_sach` (`ma_nguoi_dung`, `ma_sach`),
    CONSTRAINT `fk_tombstone_nguoi_dung` FOREIGN KEY (`ma_nguoi_dung`)
        REFERENCES `nguoi_dung` (`ma_nguoi_dung`) ON DELETE CASCADE,
    CONSTRAINT `fk_tombstone_sach` FOREIGN KEY (`ma_sach`)
        REFERENCES `sach` (`ma_sach`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Danh gia dang bi an tai thoi diem migrate cung phai co tombstone, neu khong
-- thi dung mot lan xoa la go duoc dau kiem duyet co san.
INSERT IGNORE INTO `danhgia_an_tombstone` (`ma_nguoi_dung`, `ma_sach`, `tao_luc`)
SELECT d.`ma_nguoi_dung`, d.`ma_sach`, NOW(6)
FROM `danhgia` d
WHERE d.`tung_bi_an` = 1;

-- ---------------------------------------------------------------------
-- 4) Noi danh gia cu voi don DA_GIAO co san.
--    Chon don moi nhat khi co nhieu don thoa — mot nguoi mua lai cung cuon
--    sach nhieu lan la chuyen binh thuong.
-- ---------------------------------------------------------------------
UPDATE `danhgia` d
SET d.`ma_don_hang` = (
    SELECT MAX(dh.`ma_don_hang`)
    FROM `don_hang` dh
    JOIN `chi_tiet_don_hang` ct ON ct.`ma_don_hang` = dh.`ma_don_hang`
    WHERE dh.`ma_nguoi_dung` = d.`ma_nguoi_dung`
      AND ct.`ma_sach` = d.`ma_sach`
      AND dh.`trang_thai_giao_hang` = 2
)
WHERE d.`ma_don_hang` IS NULL;

-- ---------------------------------------------------------------------
-- 5) Danh gia con lai chua co don that: sinh don demo.
--
--    `checkout_idempotency_key` lam moc noi giua hai buoc INSERT va buoc
--    UPDATE. Khong the dung PRIMARY KEY tuong minh theo khuon V4 o day: so
--    luong va danh tinh don phu thuoc du lieu danh gia thuc te cua tung
--    database, nen mot PK chep tay se dam vao don that tren database khac.
--    V9 da dat UNIQUE (ma_nguoi_dung, checkout_idempotency_key) nen moc nay
--    la duy nhat theo dung nghia can dung.
--
--    `trang_thai_thanh_toan = 0` la co y: doanh thu chi cong don da thanh
--    toan, nen don demo khong the bom doanh thu ke ca neu co la_don_demo bi
--    bo qua o dau do. Hai lop phong ve, khong phai mot.
-- ---------------------------------------------------------------------
INSERT INTO `don_hang` (
    `ngay_tao`, `dia_chi_mua_hang`, `dia_chi_nhan_hang`,
    `tong_tien_san_pham`, `chi_phi_giao_hang`, `chi_phi_thanh_toan`, `tong_tien`,
    `ho_ten`, `so_dien_thoai`, `trang_thai_thanh_toan`, `trang_thai_giao_hang`,
    `ma_nguoi_dung`, `checkout_idempotency_key`, `la_don_demo`
)
SELECT
    COALESCE(d.`timestamp`, NOW(6)), 'Đơn demo (V12)', 'Đơn demo (V12)',
    0, 0, 0, 0,
    'Đơn demo', '0000000000', 0, 2,
    d.`ma_nguoi_dung`, CONCAT('demo-review-', d.`ma_danh_gia`), 1
FROM `danhgia` d
WHERE d.`ma_don_hang` IS NULL;

INSERT INTO `chi_tiet_don_hang` (`so_luong`, `gia_ban`, `danh_gia`, `ma_sach`, `ma_don_hang`)
SELECT 1, COALESCE(s.`gia_ban`, 0), 0, d.`ma_sach`, dh.`ma_don_hang`
FROM `danhgia` d
JOIN `don_hang` dh
    ON  dh.`ma_nguoi_dung` = d.`ma_nguoi_dung`
    AND dh.`checkout_idempotency_key` = CONCAT('demo-review-', d.`ma_danh_gia`)
JOIN `sach` s ON s.`ma_sach` = d.`ma_sach`
WHERE d.`ma_don_hang` IS NULL;

UPDATE `danhgia` d
JOIN `don_hang` dh
    ON  dh.`ma_nguoi_dung` = d.`ma_nguoi_dung`
    AND dh.`checkout_idempotency_key` = CONCAT('demo-review-', d.`ma_danh_gia`)
SET d.`ma_don_hang` = dh.`ma_don_hang`
WHERE d.`ma_don_hang` IS NULL;
