ALTER TABLE `hinh_thuc_thanh_toan`
ADD COLUMN `ma_code` VARCHAR(50) DEFAULT NULL AFTER `ten_hinh_thuc_thanh_toan`;

UPDATE `hinh_thuc_thanh_toan`
SET `ma_code` = 'COD'
WHERE `ma_hinh_thuc_thanh_toan` = 1 AND (`ma_code` IS NULL OR `ma_code` = '');

UPDATE `hinh_thuc_thanh_toan`
SET `ma_code` = 'VNPAY'
WHERE `ma_hinh_thuc_thanh_toan` = 2 AND (`ma_code` IS NULL OR `ma_code` = '');
