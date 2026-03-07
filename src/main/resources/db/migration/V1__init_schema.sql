-- =============================================
-- V1: Baseline schema — all 17 JPA entity tables
-- =============================================

-- ----------------------------
-- Independent lookup tables
-- ----------------------------

CREATE TABLE IF NOT EXISTS `quyen` (
    `ma_quyen` INT NOT NULL AUTO_INCREMENT,
    `ten_quyen` VARCHAR(255) DEFAULT NULL,
    PRIMARY KEY (`ma_quyen`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `nha_cung_cap` (
    `ma_nha_cung_cap` INT NOT NULL AUTO_INCREMENT,
    `ten_nha_cung_cap` VARCHAR(255) DEFAULT NULL,
    PRIMARY KEY (`ma_nha_cung_cap`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `the_loai` (
    `ma_the_loai` INT NOT NULL AUTO_INCREMENT,
    `ten_the_loai` VARCHAR(255) DEFAULT NULL,
    PRIMARY KEY (`ma_the_loai`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `hinh_thuc_giao_hang` (
    `ma_hinh_thuc_giao_hang` INT NOT NULL AUTO_INCREMENT,
    `chi_phi_giao_hang` DOUBLE DEFAULT NULL,
    `mo_ta` VARCHAR(255) DEFAULT NULL,
    `ten_hinh_thuc_giao_hang` VARCHAR(255) DEFAULT NULL,
    PRIMARY KEY (`ma_hinh_thuc_giao_hang`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `hinh_thuc_thanh_toan` (
    `ma_hinh_thuc_thanh_toan` INT NOT NULL AUTO_INCREMENT,
    `chi_phi_thanh_toan` DOUBLE DEFAULT NULL,
    `mo_ta` VARCHAR(255) DEFAULT NULL,
    `ten_hinh_thuc_thanh_toan` VARCHAR(255) DEFAULT NULL,
    PRIMARY KEY (`ma_hinh_thuc_thanh_toan`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ----------------------------
-- nguoi_dung
-- ----------------------------

CREATE TABLE IF NOT EXISTS `nguoi_dung` (
    `ma_nguoi_dung` INT NOT NULL AUTO_INCREMENT,
    `ho_dem` VARCHAR(256) DEFAULT NULL,
    `ten` VARCHAR(256) DEFAULT NULL,
    `ten_dang_nhap` VARCHAR(255) DEFAULT NULL,
    `mat_khau` VARCHAR(512) DEFAULT NULL,
    `gioi_tinh` CHAR(1) DEFAULT NULL,
    `email` VARCHAR(255) DEFAULT NULL,
    `so_dien_thoai` VARCHAR(255) DEFAULT NULL,
    `dia_chi_mua_hang` VARCHAR(255) DEFAULT NULL,
    `dia_chi_giao_hang` VARCHAR(255) DEFAULT NULL,
    `da_kich_hoat` TINYINT(1) NOT NULL DEFAULT 1,
    `ma_kich_hoat` VARCHAR(255) DEFAULT NULL,
    `reset_password_token` VARCHAR(255) DEFAULT NULL,
    `reset_password_token_expiry` DATETIME(6) DEFAULT NULL,
    PRIMARY KEY (`ma_nguoi_dung`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ----------------------------
-- sach
-- ----------------------------

CREATE TABLE IF NOT EXISTS `sach` (
    `ma_sach` INT NOT NULL AUTO_INCREMENT,
    `ten_sach` VARCHAR(256) DEFAULT NULL,
    `ten_tac_gia` VARCHAR(512) DEFAULT NULL,
    `isbn` VARCHAR(256) DEFAULT NULL,
    `mo_ta` TEXT DEFAULT NULL,
    `mo_ta_ngan` TEXT DEFAULT NULL,
    `mo_ta_chi_tiet` LONGTEXT DEFAULT NULL,
    `gia_niem_yet` DOUBLE DEFAULT NULL,
    `gia_ban` DOUBLE DEFAULT NULL,
    `so_luong` INT DEFAULT NULL,
    `trung_binh_xep_hang` DOUBLE DEFAULT NULL,
    `slug` VARCHAR(512) DEFAULT NULL,
    `is_active` INT DEFAULT 1,
    `created_at` DATETIME(6) DEFAULT NULL,
    `updated_at` DATETIME(6) DEFAULT NULL,
    `ma_nha_cung_cap` INT DEFAULT NULL,
    PRIMARY KEY (`ma_sach`),
    UNIQUE KEY `uk_sach_slug` (`slug`),
    KEY `FK8sg5q06i3sipcgkig31l2nxgi` (`ma_nha_cung_cap`),
    CONSTRAINT `FK8sg5q06i3sipcgkig31l2nxgi` FOREIGN KEY (`ma_nha_cung_cap`) REFERENCES `nha_cung_cap` (`ma_nha_cung_cap`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ----------------------------
-- sach_thong_tin_chi_tiet (1:1 with sach)
-- ----------------------------

CREATE TABLE IF NOT EXISTS `sach_thong_tin_chi_tiet` (
    `ma_sach` INT NOT NULL,
    `cong_ty_phat_hanh` VARCHAR(255) DEFAULT NULL,
    `nha_xuat_ban` VARCHAR(255) DEFAULT NULL,
    `ngay_xuat_ban` DATE DEFAULT NULL,
    `so_trang` INT DEFAULT NULL,
    `loai_bia` VARCHAR(100) DEFAULT NULL,
    `ngon_ngu` VARCHAR(100) DEFAULT NULL,
    `kich_thuoc` VARCHAR(100) DEFAULT NULL,
    `trong_luong_gram` INT DEFAULT NULL,
    `phien_ban` VARCHAR(100) DEFAULT NULL,
    PRIMARY KEY (`ma_sach`),
    CONSTRAINT `fk_sach_thong_tin_chi_tiet_sach` FOREIGN KEY (`ma_sach`) REFERENCES `sach` (`ma_sach`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ----------------------------
-- hinh_anh
-- ----------------------------

CREATE TABLE IF NOT EXISTS `hinh_anh` (
    `ma_hinh_anh` INT NOT NULL AUTO_INCREMENT,
    `ten_hinh_anh` VARCHAR(256) DEFAULT NULL,
    `la_icon` BIT(1) DEFAULT NULL,
    `url_hinh` LONGTEXT DEFAULT NULL,
    `du_lieu_anh` LONGTEXT DEFAULT NULL,
    `cloudinary_public_id` VARCHAR(255) DEFAULT NULL,
    `ma_sach` INT NOT NULL,
    PRIMARY KEY (`ma_hinh_anh`),
    KEY `FKpo927p9w9nbmrskdq2y0lt06f` (`ma_sach`),
    KEY `idx_hinh_anh_cloudinary_public_id` (`cloudinary_public_id`),
    CONSTRAINT `FKpo927p9w9nbmrskdq2y0lt06f` FOREIGN KEY (`ma_sach`) REFERENCES `sach` (`ma_sach`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ----------------------------
-- danhgia (su_danh_gia)
-- ----------------------------

CREATE TABLE IF NOT EXISTS `danhgia` (
    `ma_danh_gia` BIGINT NOT NULL AUTO_INCREMENT,
    `diem_xep_hang` FLOAT DEFAULT NULL,
    `nhan_xet` TEXT DEFAULT NULL,
    `timestamp` DATETIME(6) DEFAULT NULL,
    `is_active` INT DEFAULT NULL,
    `ma_nguoi_dung` INT NOT NULL,
    `ma_sach` INT NOT NULL,
    PRIMARY KEY (`ma_danh_gia`),
    KEY `FKdcarac5pk10os8olf5inbokt9` (`ma_nguoi_dung`),
    KEY `FK416ln6muqja7vlts1t0w74pjl` (`ma_sach`),
    CONSTRAINT `FKdcarac5pk10os8olf5inbokt9` FOREIGN KEY (`ma_nguoi_dung`) REFERENCES `nguoi_dung` (`ma_nguoi_dung`),
    CONSTRAINT `FK416ln6muqja7vlts1t0w74pjl` FOREIGN KEY (`ma_sach`) REFERENCES `sach` (`ma_sach`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ----------------------------
-- don_hang
-- ----------------------------

CREATE TABLE IF NOT EXISTS `don_hang` (
    `ma_don_hang` INT NOT NULL AUTO_INCREMENT,
    `ngay_tao` DATETIME(6) DEFAULT NULL,
    `dia_chi_mua_hang` VARCHAR(512) DEFAULT NULL,
    `dia_chi_nhan_hang` VARCHAR(512) DEFAULT NULL,
    `tong_tien_san_pham` DOUBLE DEFAULT NULL,
    `chi_phi_giao_hang` DOUBLE DEFAULT NULL,
    `chi_phi_thanh_toan` DOUBLE DEFAULT NULL,
    `tong_tien` DOUBLE DEFAULT NULL,
    `ho_ten` VARCHAR(255) DEFAULT NULL,
    `so_dien_thoai` VARCHAR(255) DEFAULT NULL,
    `trang_thai_thanh_toan` INT DEFAULT NULL,
    `trang_thai_giao_hang` INT DEFAULT NULL,
    `ma_nguoi_dung` INT NOT NULL,
    `ma_hinh_thuc_thanh_toan` INT DEFAULT NULL,
    `ma_hinh_thuc_giao_hang` INT DEFAULT NULL,
    PRIMARY KEY (`ma_don_hang`),
    KEY `FKfiwa8ckswsiue2f0ho0jbt3l6` (`ma_nguoi_dung`),
    KEY `FK4b21m3bpem065jgpwcu5krx9n` (`ma_hinh_thuc_thanh_toan`),
    KEY `FKq17fuwcdxhmq03i929cximt15` (`ma_hinh_thuc_giao_hang`),
    CONSTRAINT `FKfiwa8ckswsiue2f0ho0jbt3l6` FOREIGN KEY (`ma_nguoi_dung`) REFERENCES `nguoi_dung` (`ma_nguoi_dung`),
    CONSTRAINT `FK4b21m3bpem065jgpwcu5krx9n` FOREIGN KEY (`ma_hinh_thuc_thanh_toan`) REFERENCES `hinh_thuc_thanh_toan` (`ma_hinh_thuc_thanh_toan`),
    CONSTRAINT `FKq17fuwcdxhmq03i929cximt15` FOREIGN KEY (`ma_hinh_thuc_giao_hang`) REFERENCES `hinh_thuc_giao_hang` (`ma_hinh_thuc_giao_hang`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ----------------------------
-- chi_tiet_don_hang
-- ----------------------------

CREATE TABLE IF NOT EXISTS `chi_tiet_don_hang` (
    `ma_chi_tiet_don_hang` INT NOT NULL AUTO_INCREMENT,
    `so_luong` INT DEFAULT NULL,
    `gia_ban` DOUBLE DEFAULT NULL,
    `danh_gia` BIT(1) NOT NULL,
    `ma_sach` INT NOT NULL,
    `ma_don_hang` INT NOT NULL,
    PRIMARY KEY (`ma_chi_tiet_don_hang`),
    KEY `FKl4ue6tf96ltdeqjhjmhr3jbfd` (`ma_sach`),
    KEY `FK9wl3houbukbxpixsut6uvojhy` (`ma_don_hang`),
    CONSTRAINT `FKl4ue6tf96ltdeqjhjmhr3jbfd` FOREIGN KEY (`ma_sach`) REFERENCES `sach` (`ma_sach`),
    CONSTRAINT `FK9wl3houbukbxpixsut6uvojhy` FOREIGN KEY (`ma_don_hang`) REFERENCES `don_hang` (`ma_don_hang`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ----------------------------
-- Join tables
-- ----------------------------

CREATE TABLE IF NOT EXISTS `nguoidung_quyen` (
    `ma_nguoi_dung` INT NOT NULL,
    `ma_quyen` INT NOT NULL,
    KEY `FKn3frlqta25h71x1rxbwsrwlog` (`ma_nguoi_dung`),
    KEY `FKm3wbqmhco8hugejjxevdik3s0` (`ma_quyen`),
    CONSTRAINT `FKn3frlqta25h71x1rxbwsrwlog` FOREIGN KEY (`ma_nguoi_dung`) REFERENCES `nguoi_dung` (`ma_nguoi_dung`),
    CONSTRAINT `FKm3wbqmhco8hugejjxevdik3s0` FOREIGN KEY (`ma_quyen`) REFERENCES `quyen` (`ma_quyen`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `sach_theloai` (
    `ma_sach` INT NOT NULL,
    `ma_the_loai` INT NOT NULL,
    KEY `FKr3e8lxxacb4f2ptpinhy5omqs` (`ma_sach`),
    KEY `FK44fgyw6ct3r2xrunide3milso` (`ma_the_loai`),
    CONSTRAINT `FKr3e8lxxacb4f2ptpinhy5omqs` FOREIGN KEY (`ma_sach`) REFERENCES `sach` (`ma_sach`),
    CONSTRAINT `FK44fgyw6ct3r2xrunide3milso` FOREIGN KEY (`ma_the_loai`) REFERENCES `the_loai` (`ma_the_loai`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ----------------------------
-- sach_yeu_thich
-- ----------------------------

CREATE TABLE IF NOT EXISTS `sach_yeu_thich` (
    `ma_sach_yeu_thich` INT NOT NULL AUTO_INCREMENT,
    `ma_nguoi_dung` INT NOT NULL,
    `ma_sach` INT NOT NULL,
    PRIMARY KEY (`ma_sach_yeu_thich`),
    KEY `FK8tc0089etalb2cj76op99gwuk` (`ma_nguoi_dung`),
    KEY `FK6mpc8yjdhjph8u9bvcyib0u03` (`ma_sach`),
    CONSTRAINT `FK8tc0089etalb2cj76op99gwuk` FOREIGN KEY (`ma_nguoi_dung`) REFERENCES `nguoi_dung` (`ma_nguoi_dung`),
    CONSTRAINT `FK6mpc8yjdhjph8u9bvcyib0u03` FOREIGN KEY (`ma_sach`) REFERENCES `sach` (`ma_sach`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ----------------------------
-- gio_hang
-- ----------------------------

CREATE TABLE IF NOT EXISTS `gio_hang` (
    `ma_gio_hang` INT NOT NULL AUTO_INCREMENT,
    `so_luong` INT DEFAULT NULL,
    `ma_sach` INT NOT NULL,
    `ma_nguoi_dung` INT NOT NULL,
    PRIMARY KEY (`ma_gio_hang`),
    KEY `fk_gio_hang_sach` (`ma_sach`),
    KEY `fk_gio_hang_nguoi_dung` (`ma_nguoi_dung`),
    CONSTRAINT `fk_gio_hang_sach` FOREIGN KEY (`ma_sach`) REFERENCES `sach` (`ma_sach`),
    CONSTRAINT `fk_gio_hang_nguoi_dung` FOREIGN KEY (`ma_nguoi_dung`) REFERENCES `nguoi_dung` (`ma_nguoi_dung`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ----------------------------
-- dia_chi_giao_hang
-- ----------------------------

CREATE TABLE IF NOT EXISTS `dia_chi_giao_hang` (
    `ma_dia_chi` INT NOT NULL AUTO_INCREMENT,
    `ma_nguoi_dung` INT NOT NULL,
    `ho_ten` VARCHAR(256) DEFAULT NULL,
    `so_dien_thoai` VARCHAR(20) DEFAULT NULL,
    `dia_chi_day_du` VARCHAR(512) DEFAULT NULL,
    `mac_dinh` BIT(1) DEFAULT NULL,
    PRIMARY KEY (`ma_dia_chi`),
    KEY `fk_dia_chi_nguoi_dung` (`ma_nguoi_dung`),
    CONSTRAINT `fk_dia_chi_nguoi_dung` FOREIGN KEY (`ma_nguoi_dung`) REFERENCES `nguoi_dung` (`ma_nguoi_dung`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ----------------------------
-- coupon
-- ----------------------------

CREATE TABLE IF NOT EXISTS `coupon` (
    `ma_coupon` INT NOT NULL AUTO_INCREMENT,
    `ma` VARCHAR(50) NOT NULL,
    `loai` VARCHAR(255) DEFAULT NULL,
    `gia_tri_giam` DOUBLE DEFAULT NULL,
    `gia_tri_toi_thieu` DOUBLE DEFAULT NULL,
    `han_su_dung` DATETIME(6) DEFAULT NULL,
    `so_luong_toi_da` INT DEFAULT NULL,
    `da_su_dung` INT DEFAULT 0,
    `is_active` BIT(1) DEFAULT NULL,
    PRIMARY KEY (`ma_coupon`),
    UNIQUE KEY `uk_coupon_ma` (`ma`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
