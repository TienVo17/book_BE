-- Serialize legacy wishlist writers until the unique index is installed. ALTER
-- may release an explicit table lock, but by then the database constraint is the
-- writer guard; no separate DML statement can run inside the ALTER operation.
-- Keep Flyway's history table available on the same connection while MySQL is
-- in explicit locked-tables mode; Flyway records V20 immediately after the SQL
-- script completes.
LOCK TABLES
    `sach_yeu_thich` WRITE,
    `sach_yeu_thich` AS `duplicate` WRITE,
    `sach_yeu_thich` AS `keeper` WRITE,
    `flyway_schema_history` WRITE;

-- Wishlist rows have no user-visible attributes, so duplicate legacy rows can
-- be merged safely by retaining the smallest primary key for each user-book pair.
DELETE duplicate
FROM `sach_yeu_thich` AS duplicate
JOIN `sach_yeu_thich` AS keeper
    ON keeper.`ma_nguoi_dung` = duplicate.`ma_nguoi_dung`
    AND keeper.`ma_sach` = duplicate.`ma_sach`
    AND keeper.`ma_sach_yeu_thich` < duplicate.`ma_sach_yeu_thich`;

ALTER TABLE `sach_yeu_thich`
    ADD CONSTRAINT `uk_sach_yeu_thich_nguoi_sach`
        UNIQUE (`ma_nguoi_dung`, `ma_sach`);

UNLOCK TABLES;
