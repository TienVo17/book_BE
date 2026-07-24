-- V1 predates Aiven's requirement that every table have a primary key.
-- Precreate its two join tables with natural composite keys. V1 uses IF NOT EXISTS,
-- so it leaves these definitions intact and V8 adds the foreign keys afterward.
CREATE TABLE IF NOT EXISTS `nguoidung_quyen` (
    `ma_nguoi_dung` INT NOT NULL,
    `ma_quyen` INT NOT NULL,
    PRIMARY KEY (`ma_nguoi_dung`, `ma_quyen`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `sach_theloai` (
    `ma_sach` INT NOT NULL,
    `ma_the_loai` INT NOT NULL,
    PRIMARY KEY (`ma_sach`, `ma_the_loai`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
