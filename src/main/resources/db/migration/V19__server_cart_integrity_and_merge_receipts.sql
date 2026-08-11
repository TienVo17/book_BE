-- Loai bo cac row legacy khong the dai dien cho mot cart line hop le.
DELETE FROM `gio_hang`
WHERE `so_luong` IS NULL OR `so_luong` <= 0;

-- Cong quantity cua duplicate row bang kieu rong, cap tai Integer.MAX_VALUE,
-- sau do giu row co primary key nho nhat cho moi cap user-book.
UPDATE `gio_hang` AS keeper
JOIN (
    SELECT
        `ma_nguoi_dung`,
        `ma_sach`,
        MIN(`ma_gio_hang`) AS `ma_gio_hang_giu_lai`,
        LEAST(SUM(CAST(`so_luong` AS UNSIGNED)), 2147483647) AS `tong_so_luong`
    FROM `gio_hang`
    GROUP BY `ma_nguoi_dung`, `ma_sach`
) AS grouped
    ON keeper.`ma_gio_hang` = grouped.`ma_gio_hang_giu_lai`
SET keeper.`so_luong` = grouped.`tong_so_luong`;

DELETE duplicate
FROM `gio_hang` AS duplicate
JOIN `gio_hang` AS keeper
    ON keeper.`ma_nguoi_dung` = duplicate.`ma_nguoi_dung`
    AND keeper.`ma_sach` = duplicate.`ma_sach`
    AND keeper.`ma_gio_hang` < duplicate.`ma_gio_hang`;

ALTER TABLE `gio_hang`
    MODIFY COLUMN `so_luong` INT NOT NULL,
    ADD CONSTRAINT `uk_gio_hang_nguoi_sach`
        UNIQUE (`ma_nguoi_dung`, `ma_sach`),
    ADD CONSTRAINT `chk_gio_hang_so_luong_duong`
        CHECK (`so_luong` > 0);

CREATE TABLE `gio_hang_merge_receipt` (
    `ma_gio_hang_merge_receipt` BIGINT NOT NULL AUTO_INCREMENT,
    `ma_nguoi_dung` INT NOT NULL,
    `idempotency_key` VARCHAR(100) NOT NULL,
    `request_fingerprint` VARCHAR(64) NOT NULL,
    `response_json` LONGTEXT NOT NULL,
    `created_at` DATETIME(6) NOT NULL,
    PRIMARY KEY (`ma_gio_hang_merge_receipt`),
    INDEX `idx_gio_hang_merge_created_id`
        (`created_at`, `ma_gio_hang_merge_receipt`),
    CONSTRAINT `uk_gio_hang_merge_nguoi_key`
        UNIQUE (`ma_nguoi_dung`, `idempotency_key`),
    CONSTRAINT `fk_gio_hang_merge_receipt_nguoi_dung`
        FOREIGN KEY (`ma_nguoi_dung`)
        REFERENCES `nguoi_dung` (`ma_nguoi_dung`)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
