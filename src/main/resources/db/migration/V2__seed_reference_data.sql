-- =============================================
-- V2: Seed reference/lookup data required for app to function
-- =============================================

-- Roles
INSERT IGNORE INTO `quyen` (`ma_quyen`, `ten_quyen`) VALUES (1, 'ADMIN');
INSERT IGNORE INTO `quyen` (`ma_quyen`, `ten_quyen`) VALUES (2, 'USER');
INSERT IGNORE INTO `quyen` (`ma_quyen`, `ten_quyen`) VALUES (3, 'STAFF');

-- Shipping methods
INSERT IGNORE INTO `hinh_thuc_giao_hang` (`ma_hinh_thuc_giao_hang`, `chi_phi_giao_hang`, `mo_ta`, `ten_hinh_thuc_giao_hang`) VALUES
(1, 10000, 'Giao hàng tận nơi', 'Giao hàng tận nơi'),
(2, 0, 'Tự lấy hàng tại cửa hàng', 'Tự lấy hàng tại cửa hàng');

-- Payment methods
INSERT IGNORE INTO `hinh_thuc_thanh_toan` (`ma_hinh_thuc_thanh_toan`, `chi_phi_thanh_toan`, `mo_ta`, `ten_hinh_thuc_thanh_toan`) VALUES
(1, 0, 'Thanh toán khi nhận hàng', 'Thanh toán khi nhận hàng'),
(2, 0, 'Chuyển khoản ngân hàng', 'Chuyển khoản ngân hàng');
