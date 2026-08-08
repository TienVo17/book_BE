-- =====================================================================
-- V17 — Dang ky nhan tin (newsletter).
--
-- THUAN ADDITIVE, chay lai vo hai.
--
-- Truoc migration nay, o "Dang ky nhan tin" o footer la mot form trang tri:
-- nut mang type="button", khong handler, khong endpoint. Khach go email, bam,
-- va khong co gi xay ra ca. Bang nay la noi luu that.
--
-- Kieu cot chon theo dung thu Hibernate sinh ra cho tung kieu Java, vi
-- `ddl-auto=validate` se chan khoi dong khi lech:
--   Boolean          -> BIT(1)      (giong `la_icon`, `mac_dinh`, `is_active` o V1)
--   Instant/Timestamp-> DATETIME(6) (giong `ngay_tao`, `timestamp` o V1)
--   String           -> VARCHAR     (V16 phai sua vi mot cot lo tay dat CHAR)
--
-- `ma_huy` la khoa ngau nhien de huy dang ky. Neu huy bang chinh dia chi email
-- thi bat ky ai cung go duoc email nguoi khac de cho ho ngung nhan tin.
-- =====================================================================

CREATE TABLE IF NOT EXISTS `dang_ky_nhan_tin` (
    `ma_dang_ky`   BIGINT       NOT NULL AUTO_INCREMENT,
    `email`        VARCHAR(255) NOT NULL,
    `ma_huy`       VARCHAR(36)  NOT NULL,
    `ngay_dang_ky` DATETIME(6)  NOT NULL,
    `da_huy`       BIT(1)       NOT NULL DEFAULT b'0',
    PRIMARY KEY (`ma_dang_ky`),
    UNIQUE KEY `uq_dang_ky_nhan_tin_email` (`email`),
    UNIQUE KEY `uq_dang_ky_nhan_tin_ma_huy` (`ma_huy`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
