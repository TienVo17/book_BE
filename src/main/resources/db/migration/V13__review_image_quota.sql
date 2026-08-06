-- =====================================================================
-- V13 — Han ngach anh danh gia tron doi cho moi nguoi dung.
--
-- THUAN ADDITIVE, chay lai vo hai.
--
-- Vi sao can mot cot rieng thay vi dem dong trong `danhgia_hinh_anh`:
-- gioi han "5 anh moi danh gia" khong phai han ngach. Tai 5 anh, xoa, tai 5 anh
-- nua, lap vo han — moi lan deu ton bang thong va dung luong Cloudinary. Mot bo
-- dem chi cong len, khong bi buoc xoa lam giam, moi thuc su chan duoc vong do.
--
-- Cap phat so: da chay `ls db/migration | sort -V | tail -1` truoc khi tao file
-- nay, ket qua V12. Ke hoach ghi phase 6 "khong co migration moi" nhung dong
-- thoi doi han ngach tron doi; hai cau do khong the cung dung. Migration don
-- dep cua phase 7 lui xuong V14.
-- =====================================================================

SET @co_han_ngach := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'nguoi_dung'
      AND COLUMN_NAME = 'so_anh_danh_gia_da_dung'
);
SET @sql_han_ngach := IF(@co_han_ngach = 0,
    'ALTER TABLE `nguoi_dung` ADD COLUMN `so_anh_danh_gia_da_dung` INT NOT NULL DEFAULT 0',
    'DO 0');
PREPARE stmt_han_ngach FROM @sql_han_ngach;
EXECUTE stmt_han_ngach;
DEALLOCATE PREPARE stmt_han_ngach;

-- Backfill tu so anh dang co. Chi dung o lan tao cot: sau do bo dem chi di len,
-- va chay lai buoc nay se lam no tut xuong theo so anh da bi xoa — dung cai ma
-- han ngach sinh ra de chan.
SET @sql_backfill := IF(@co_han_ngach = 0,
    'UPDATE `nguoi_dung` u SET u.`so_anh_danh_gia_da_dung` = (
         SELECT COUNT(*) FROM `danhgia_hinh_anh` a
         JOIN `danhgia` d ON d.`ma_danh_gia` = a.`ma_danh_gia`
         WHERE d.`ma_nguoi_dung` = u.`ma_nguoi_dung`
     )',
    'DO 0');
PREPARE stmt_backfill FROM @sql_backfill;
EXECUTE stmt_backfill;
DEALLOCATE PREPARE stmt_backfill;
