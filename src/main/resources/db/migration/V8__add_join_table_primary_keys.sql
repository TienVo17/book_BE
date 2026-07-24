-- Aiven MySQL requires a primary key on every table.
-- beforeMigrate precreates these tables for fresh databases; this migration also
-- upgrades databases that already ran V1 without the composite keys.

SET @has_nguoidung_quyen_pk = (
    SELECT COUNT(*)
    FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE()
      AND table_name = 'nguoidung_quyen'
      AND constraint_type = 'PRIMARY KEY'
);
SET @sql = IF(
    @has_nguoidung_quyen_pk = 0,
    'ALTER TABLE `nguoidung_quyen` ADD PRIMARY KEY (`ma_nguoi_dung`, `ma_quyen`)',
    'SELECT 1'
);
PREPARE statement FROM @sql;
EXECUTE statement;
DEALLOCATE PREPARE statement;

SET @has_sach_theloai_pk = (
    SELECT COUNT(*)
    FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE()
      AND table_name = 'sach_theloai'
      AND constraint_type = 'PRIMARY KEY'
);
SET @sql = IF(
    @has_sach_theloai_pk = 0,
    'ALTER TABLE `sach_theloai` ADD PRIMARY KEY (`ma_sach`, `ma_the_loai`)',
    'SELECT 1'
);
PREPARE statement FROM @sql;
EXECUTE statement;
DEALLOCATE PREPARE statement;

SET @has_nguoidung_quyen_user_fk = (
    SELECT COUNT(*)
    FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE()
      AND table_name = 'nguoidung_quyen'
      AND constraint_name = 'FKn3frlqta25h71x1rxbwsrwlog'
      AND constraint_type = 'FOREIGN KEY'
);
SET @sql = IF(
    @has_nguoidung_quyen_user_fk = 0,
    'ALTER TABLE `nguoidung_quyen` ADD CONSTRAINT `FKn3frlqta25h71x1rxbwsrwlog` FOREIGN KEY (`ma_nguoi_dung`) REFERENCES `nguoi_dung` (`ma_nguoi_dung`)',
    'SELECT 1'
);
PREPARE statement FROM @sql;
EXECUTE statement;
DEALLOCATE PREPARE statement;

SET @has_nguoidung_quyen_role_fk = (
    SELECT COUNT(*)
    FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE()
      AND table_name = 'nguoidung_quyen'
      AND constraint_name = 'FKm3wbqmhco8hugejjxevdik3s0'
      AND constraint_type = 'FOREIGN KEY'
);
SET @sql = IF(
    @has_nguoidung_quyen_role_fk = 0,
    'ALTER TABLE `nguoidung_quyen` ADD CONSTRAINT `FKm3wbqmhco8hugejjxevdik3s0` FOREIGN KEY (`ma_quyen`) REFERENCES `quyen` (`ma_quyen`)',
    'SELECT 1'
);
PREPARE statement FROM @sql;
EXECUTE statement;
DEALLOCATE PREPARE statement;

SET @has_sach_theloai_book_fk = (
    SELECT COUNT(*)
    FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE()
      AND table_name = 'sach_theloai'
      AND constraint_name = 'FKr3e8lxxacb4f2ptpinhy5omqs'
      AND constraint_type = 'FOREIGN KEY'
);
SET @sql = IF(
    @has_sach_theloai_book_fk = 0,
    'ALTER TABLE `sach_theloai` ADD CONSTRAINT `FKr3e8lxxacb4f2ptpinhy5omqs` FOREIGN KEY (`ma_sach`) REFERENCES `sach` (`ma_sach`)',
    'SELECT 1'
);
PREPARE statement FROM @sql;
EXECUTE statement;
DEALLOCATE PREPARE statement;

SET @has_sach_theloai_category_fk = (
    SELECT COUNT(*)
    FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE()
      AND table_name = 'sach_theloai'
      AND constraint_name = 'FK44fgyw6ct3r2xrunide3milso'
      AND constraint_type = 'FOREIGN KEY'
);
SET @sql = IF(
    @has_sach_theloai_category_fk = 0,
    'ALTER TABLE `sach_theloai` ADD CONSTRAINT `FK44fgyw6ct3r2xrunide3milso` FOREIGN KEY (`ma_the_loai`) REFERENCES `the_loai` (`ma_the_loai`)',
    'SELECT 1'
);
PREPARE statement FROM @sql;
EXECUTE statement;
DEALLOCATE PREPARE statement;
