-- =====================================================================
-- V16 — noi_dung_sha256 phai la VARCHAR(64), khong phai CHAR(64).
--
-- V14 tao cot bang CHAR(64) trong khi entity DanhGiaHinhAnh map String voi
-- length = 64, tuc VARCHAR. Voi ddl-auto=validate (cau hinh that cua
-- application.properties) Hibernate tu choi khoi tao entityManagerFactory:
--   wrong column type encountered in column [noi_dung_sha256]
--   in table [danhgia_hinh_anh]; found [char], but expecting [varchar(64)]
-- tuc la ung dung KHONG START DUOC tren mot database da migrate day du.
--
-- Sua o day chu khong sua V14: V14 da chay tren cac database that, doi noi dung
-- cua no se lam checksum lech va Flyway tu choi migrate tiep.
--
-- Chi doi kieu cot. Gia tri hash la 64 ky tu hex nen khong dong nao bi cat.
-- MySQL auto-commit DDL, nen ALTER co information_schema guard de chay lai an toan.
-- =====================================================================

SET @kieu_hash_hien_tai := (
    SELECT `DATA_TYPE` FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'danhgia_hinh_anh'
      AND COLUMN_NAME = 'noi_dung_sha256'
);
SET @sql_hash_varchar := IF(
    @kieu_hash_hien_tai = 'char',
    'ALTER TABLE `danhgia_hinh_anh` MODIFY COLUMN `noi_dung_sha256` VARCHAR(64) NOT NULL',
    'DO 0'
);
PREPARE stmt_hash_varchar FROM @sql_hash_varchar;
EXECUTE stmt_hash_varchar;
DEALLOCATE PREPARE stmt_hash_varchar;
