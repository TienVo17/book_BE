-- =============================================
-- V3: Seed default admin user
-- Password: 1 (BCrypt hash)
-- IMPORTANT: Change this password after first login!
-- =============================================

INSERT IGNORE INTO `nguoi_dung` (`ma_nguoi_dung`, `ho_dem`, `ten`, `ten_dang_nhap`, `mat_khau`, `gioi_tinh`, `email`, `so_dien_thoai`, `dia_chi_mua_hang`, `dia_chi_giao_hang`, `da_kich_hoat`)
VALUES (1, 'Admin', 'System', 'admin', '$2a$10$B6qPwSi5FHcaX4a34FwqRuMnBGl1wz5V30js1OQ3Nm1/CCoWDkiv2', 'M', 'admin@bookstore.com', '0000000000', '', '', 1);

-- Assign ADMIN + STAFF roles to admin user
INSERT IGNORE INTO `nguoidung_quyen` (`ma_nguoi_dung`, `ma_quyen`) VALUES (1, 1);
INSERT IGNORE INTO `nguoidung_quyen` (`ma_nguoi_dung`, `ma_quyen`) VALUES (1, 3);
