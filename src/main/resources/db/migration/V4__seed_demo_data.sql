-- =============================================
-- V4: Demo data for review/testing
-- Clean version of original db/init data (deduplicated)
-- =============================================

-- ----------------------------
-- The loai (5 unique categories)
-- ----------------------------
INSERT IGNORE INTO `the_loai` (`ma_the_loai`, `ten_the_loai`) VALUES
(1, 'Khoa học'),
(2, 'Tiểu thuyết'),
(3, 'Tâm lý học'),
(4, 'Lịch sử'),
(5, 'Huyền bí');

-- ----------------------------
-- Nha cung cap
-- ----------------------------
INSERT IGNORE INTO `nha_cung_cap` (`ma_nha_cung_cap`, `ten_nha_cung_cap`) VALUES
(1, 'Kim Đồng'),
(2, 'Kim Dung');

-- ----------------------------
-- Demo users (password for all: 1 - BCrypt hash)
-- ----------------------------
INSERT IGNORE INTO `nguoi_dung` (`ma_nguoi_dung`, `ho_dem`, `ten`, `ten_dang_nhap`, `mat_khau`, `gioi_tinh`, `email`, `so_dien_thoai`, `dia_chi_mua_hang`, `dia_chi_giao_hang`, `da_kich_hoat`) VALUES
(2, 'Nguyen Van', 'Thanh', 'user1', '$2a$10$B6qPwSi5FHcaX4a34FwqRuMnBGl1wz5V30js1OQ3Nm1/CCoWDkiv2', 'M', 'nguoidung1@email.com', '0123456789', '123 Đường ABC, Quận 1, TP.HCM', '456 Đường XYZ, Quận 3, TP.HCM', 1),
(3, 'Tran Thi', 'Lan', 'user2', '$2a$10$B6qPwSi5FHcaX4a34FwqRuMnBGl1wz5V30js1OQ3Nm1/CCoWDkiv2', 'F', 'nguoidung2@email.com', '0987654321', '456 Đường XYZ, Quận 3, TP.HCM', '789 Đường DEF, Quận 5, TP.HCM', 1),
(4, 'Le Van', 'Tuan', 'user3', '$2a$10$B6qPwSi5FHcaX4a34FwqRuMnBGl1wz5V30js1OQ3Nm1/CCoWDkiv2', 'M', 'nguoidung3@email.com', '0555555555', '789 Đường DEF, Quận 5, TP.HCM', '123 Đường ABC, Quận 1, TP.HCM', 1),
(5, 'Pham Van', 'Hoa', 'user4', '$2a$10$B6qPwSi5FHcaX4a34FwqRuMnBGl1wz5V30js1OQ3Nm1/CCoWDkiv2', 'M', 'nguoidung4@email.com', '0666666666', '101 Đường GHI, Quận 7, TP.HCM', '202 Đường JKL, Quận 9, TP.HCM', 1),
(6, 'Hoang Van', 'Tinh', 'user5', '$2a$10$B6qPwSi5FHcaX4a34FwqRuMnBGl1wz5V30js1OQ3Nm1/CCoWDkiv2', 'M', 'nguoidung5@email.com', '0777777777', '303 Đường MNO, Quận Bình Thạnh', '404 Đường PQR, Quận Phú Nhuận', 1);

-- Assign roles to demo users
INSERT IGNORE INTO `nguoidung_quyen` (`ma_nguoi_dung`, `ma_quyen`) VALUES
(2, 3), -- user1 = USER
(3, 3), -- user2 = USER
(4, 3), -- user3 = USER
(5, 3), -- user4 = USER
(6, 3); -- user5 = USER

-- ----------------------------
-- Demo books (10 books with clean descriptions)
-- ----------------------------
INSERT IGNORE INTO `sach` (`ma_sach`, `isbn`, `ten_sach`, `ten_tac_gia`, `mo_ta`, `mo_ta_ngan`, `mo_ta_chi_tiet`, `gia_niem_yet`, `gia_ban`, `so_luong`, `trung_binh_xep_hang`, `slug`, `is_active`, `created_at`, `updated_at`, `ma_nha_cung_cap`) VALUES
(1, '9786043652147', 'Đắc Nhân Tâm', 'Dale Carnegie', 'Cuốn sách kinh điển về nghệ thuật giao tiếp và ứng xử', 'Cuốn sách kinh điển về nghệ thuật giao tiếp và ứng xử, giúp bạn thành công trong cuộc sống.', 'Đắc nhân tâm là cuốn sách nổi tiếng nhất, bán chạy nhất và có tầm ảnh hưởng nhất của mọi thời đại. Tác phẩm đã được chuyển ngữ sang hầu hết các thứ tiếng trên thế giới. Đây là cuốn sách duy nhất về thể loại self-help liên tục đứng đầu danh mục sách bán chạy nhất do báo The New York Times bình chọn suốt 10 năm liền.', 86000, 69000, 50, 4.5, 'dac-nhan-tam', 1, NOW(), NOW(), 1),
(2, '9786043651188', 'Nhà Giả Kim', 'Paulo Coelho', 'Tiểu thuyết kinh điển về hành trình theo đuổi ước mơ', 'Câu chuyện về cậu bé chăn cừu Santiago theo đuổi giấc mơ tìm kho báu tại Kim tự tháp Ai Cập.', 'Nhà giả kim là tiểu thuyết được xuất bản lần đầu năm 1988 bởi nhà văn người Brasil Paulo Coelho. Tác phẩm đã được dịch ra 80 thứ tiếng, bán ra hơn 150 triệu bản, trở thành một trong những cuốn sách bán chạy nhất mọi thời đại.', 79000, 59000, 35, 4.8, 'nha-gia-kim', 1, NOW(), NOW(), 1),
(3, '9786044782553', 'Sapiens: Lược Sử Loài Người', 'Yuval Noah Harari', 'Hành trình 70.000 năm của loài người từ kỷ nguyên đồ đá đến thế kỷ 21', 'Một cuốn sách lịch sử đầy tham vọng, kể lại toàn bộ lịch sử loài người.', 'Sapiens là cuốn sách kể lại toàn bộ lịch sử loài người từ thuở sơ khai đến hiện đại. Yuval Noah Harari đưa ra những góc nhìn mới mẻ về cách loài người chinh phục thế giới và tạo ra nền văn minh.', 199000, 159000, 20, 4.7, 'sapiens-luoc-su-loai-nguoi', 1, NOW(), NOW(), 2),
(4, '9786045890578', 'Tôi Tài Giỏi, Bạn Cũng Thế', 'Adam Khoo', 'Phương pháp học tập hiệu quả dành cho học sinh sinh viên', 'Cuốn sách giúp bạn phát hiện và phát huy tiềm năng bản thân trong học tập.', 'Tôi Tài Giỏi, Bạn Cũng Thế! là cuốn sách định hướng giúp học sinh, sinh viên khám phá tiềm năng bản thân và áp dụng các phương pháp học tập hiệu quả nhất.', 110000, 89000, 40, 4.3, 'toi-tai-gioi-ban-cung-the', 1, NOW(), NOW(), 1),
(5, '9786043587401', 'Nghĩ Giàu Làm Giàu', 'Napoleon Hill', 'Bí quyết thành công và làm giàu từ tư duy', 'Cuốn sách kinh điển về phát triển tư duy và chiến lược làm giàu.', 'Nghĩ giàu làm giàu là tác phẩm bán chạy mọi thời đại của Napoleon Hill. Cuốn sách đúc kết 20 năm nghiên cứu về những người giàu nhất nước Mỹ, đưa ra 13 nguyên tắc vàng để đạt thành công.', 88000, 72000, 30, 4.4, 'nghi-giau-lam-giau', 1, NOW(), NOW(), 2),
(6, '9786043457823', 'Tuổi Trẻ Đáng Giá Bao Nhiêu', 'Rosie Nguyễn', 'Hành trình khám phá bản thân dành cho người trẻ', 'Cuốn sách truyền cảm hứng cho thế hệ trẻ Việt Nam.', 'Tuổi Trẻ Đáng Giá Bao Nhiêu là cuốn sách dành cho người trẻ Việt Nam, chia sẻ về hành trình trưởng thành, du học và làm việc ở nước ngoài của tác giả.', 95000, 76000, 25, 4.1, 'tuoi-tre-dang-gia-bao-nhieu', 1, NOW(), NOW(), 1),
(7, '9786043589214', 'Cà Phê Cùng Tony', 'Tony Buổi Sáng', 'Những bài viết truyền cảm hứng sống tích cực', 'Tuyển tập những bài viết ngắn về cuộc sống, tình yêu và sự nghiệp.', 'Cà Phê Cùng Tony là tuyển tập những bài viết trên Facebook của Tony Buổi Sáng, chia sẻ về cuộc sống, tình yêu, sự nghiệp bằng lối viết nhẹ nhàng, dí dỏm.', 72000, 58000, 45, 4.0, 'ca-phe-cung-tony', 1, NOW(), NOW(), 2),
(8, '9786043124578', 'Thao Túng Tâm Lý', 'Đặng Hoàng Giang', 'Nhận diện và phòng tránh các hình thức thao túng', 'Cuốn sách giúp nhận diện các chiêu trò thao túng tâm lý trong cuộc sống.', 'Thao Túng Tâm Lý giúp bạn nhận diện những hành vi thao túng phổ biến trong các mối quan hệ và cách bảo vệ bản thân khỏi những tác động tiêu cực.', 105000, 84000, 30, 4.6, 'thao-tung-tam-ly', 1, NOW(), NOW(), 1),
(9, '9786043785412', 'Lược Sử Thời Gian', 'Stephen Hawking', 'Khám phá bí ẩn của vũ trụ từ Big Bang đến hố đen', 'Cuốn sách khoa học phổ thông kinh điển về vũ trụ và thời gian.', 'Lược sử thời gian là một trong những cuốn sách khoa học phổ thông bán chạy nhất mọi thời đại, giải thích các khái niệm phức tạp về vũ trụ theo cách dễ hiểu.', 120000, 96000, 15, 4.9, 'luoc-su-thoi-gian', 1, NOW(), NOW(), 2),
(10, '9786043965874', 'Dám Bị Ghét', 'Koga Fumitake & Kishimi Ichiro', 'Triết học Adler cho cuộc sống tự do và hạnh phúc', 'Cuốn sách giúp bạn sống tự do bằng cách chấp nhận bản thân.', 'Dám Bị Ghét giới thiệu triết học Alfred Adler qua những cuộc đối thoại giữa một triết gia và chàng thanh niên, giúp bạn tìm ra con đường sống hạnh phúc thực sự.', 89000, 71000, 35, 4.5, 'dam-bi-ghet', 1, NOW(), NOW(), 1);

-- ----------------------------
-- Book-Category mapping
-- ----------------------------
INSERT IGNORE INTO `sach_theloai` (`ma_sach`, `ma_the_loai`) VALUES
(1, 3),  -- Đắc Nhân Tâm -> Tâm lý học
(2, 2),  -- Nhà Giả Kim -> Tiểu thuyết
(2, 5),  -- Nhà Giả Kim -> Huyền bí
(3, 4),  -- Sapiens -> Lịch sử
(3, 1),  -- Sapiens -> Khoa học
(4, 3),  -- Tôi Tài Giỏi -> Tâm lý học
(5, 3),  -- Nghĩ Giàu -> Tâm lý học
(6, 2),  -- Tuổi Trẻ -> Tiểu thuyết
(7, 2),  -- Cà Phê -> Tiểu thuyết
(8, 3),  -- Thao Túng -> Tâm lý học
(9, 1),  -- Lược Sử Thời Gian -> Khoa học
(10, 3); -- Dám Bị Ghét -> Tâm lý học

-- ----------------------------
-- Demo reviews
-- ----------------------------
INSERT IGNORE INTO `danhgia` (`ma_danh_gia`, `diem_xep_hang`, `nhan_xet`, `ma_nguoi_dung`, `ma_sach`, `timestamp`, `is_active`) VALUES
(1, 5, 'Sách rất hay, nội dung dễ hiểu và áp dụng được ngay!', 2, 1, NOW(), 1),
(2, 4, 'Cuốn sách truyền cảm hứng, nhưng hơi dài', 3, 1, NOW(), 1),
(3, 5, 'Nhà Giả Kim thay đổi cách tôi nhìn cuộc sống', 2, 2, NOW(), 1),
(4, 4, 'Nội dung phong phú, rất đáng đọc', 4, 3, NOW(), 1),
(5, 3, 'Sách tạm ổn, một số phần hơi khó hiểu', 5, 3, NOW(), 1),
(6, 5, 'Cuốn sách tuyệt vời cho học sinh sinh viên', 3, 4, NOW(), 1),
(7, 4, 'Rất hữu ích cho việc phát triển bản thân', 6, 5, NOW(), 1),
(8, 5, 'Stephen Hawking giải thích vũ trụ rất hay!', 2, 9, NOW(), 1);

-- ----------------------------
-- Demo orders
-- ----------------------------
INSERT IGNORE INTO `don_hang` (`ma_don_hang`, `ngay_tao`, `dia_chi_mua_hang`, `dia_chi_nhan_hang`, `tong_tien_san_pham`, `chi_phi_giao_hang`, `chi_phi_thanh_toan`, `tong_tien`, `ho_ten`, `so_dien_thoai`, `trang_thai_thanh_toan`, `trang_thai_giao_hang`, `ma_nguoi_dung`, `ma_hinh_thuc_thanh_toan`, `ma_hinh_thuc_giao_hang`) VALUES
(1, '2025-01-15 10:30:00', '123 Đường ABC, Quận 1', '456 Đường XYZ, Quận 3', 128000, 10000, 0, 138000, 'Nguyen Van Thanh', '0123456789', 1, 1, 2, 1, 1),
(2, '2025-01-20 14:15:00', '456 Đường XYZ, Quận 3', '789 Đường DEF, Quận 5', 218000, 0, 0, 218000, 'Tran Thi Lan', '0987654321', 1, 2, 3, 2, 2),
(3, '2025-02-05 09:00:00', '789 Đường DEF, Quận 5', '101 Đường GHI, Quận 7', 96000, 10000, 0, 106000, 'Le Van Tuan', '0555555555', 0, 0, 4, 1, 1);

-- ----------------------------
-- Demo order details
-- ----------------------------
INSERT IGNORE INTO `chi_tiet_don_hang` (`ma_chi_tiet_don_hang`, `so_luong`, `gia_ban`, `danh_gia`, `ma_sach`, `ma_don_hang`) VALUES
(1, 1, 69000, 1, 1, 1),  -- Đắc Nhân Tâm
(2, 1, 59000, 0, 2, 1),  -- Nhà Giả Kim
(3, 1, 159000, 1, 3, 2), -- Sapiens
(4, 1, 59000, 0, 2, 2),  -- Nhà Giả Kim
(5, 1, 96000, 0, 9, 3);  -- Lược Sử Thời Gian

-- ----------------------------
-- Demo hinh_anh (placeholder URLs)
-- ----------------------------
INSERT IGNORE INTO `hinh_anh` (`ma_hinh_anh`, `ten_hinh_anh`, `la_icon`, `url_hinh`, `ma_sach`) VALUES
(1, 'dac-nhan-tam.jpg', 1, 'https://salt.tikicdn.com/cache/750x750/ts/product/5e/18/24/2a6154ba08df6ce6161c13f4303fa19e.jpg', 1),
(2, 'nha-gia-kim.jpg', 1, 'https://salt.tikicdn.com/cache/750x750/ts/product/45/3b/fc/aa81d0a534b45706ae1eee1e344e80d9.jpg', 2),
(3, 'sapiens.jpg', 1, 'https://salt.tikicdn.com/cache/750x750/media/catalog/producttmp/3e/2c/d0/7ee5ce9e1875fe27658df8560024d090.jpg', 3),
(4, 'toi-tai-gioi.jpg', 1, 'https://salt.tikicdn.com/cache/750x750/ts/product/97/5f/12/4894a2e43a5824a67a8bfb5e55e8e1d5.jpg', 4),
(5, 'nghi-giau-lam-giau.jpg', 1, 'https://salt.tikicdn.com/cache/750x750/ts/product/3c/13/60/5c354241e0314b3dfa83fb4899ee5b6e.jpg', 5),
(6, 'tuoi-tre.jpg', 1, 'https://salt.tikicdn.com/cache/750x750/ts/product/6c/5c/41/3353e1b3c2a1a2c5ef30dff77c11c702.jpg', 6),
(7, 'ca-phe-cung-tony.jpg', 1, 'https://salt.tikicdn.com/cache/750x750/ts/product/e3/a1/16/bfd23c6a0a7582bed553422dd8964235.jpg', 7),
(8, 'thao-tung-tam-ly.jpg', 1, 'https://salt.tikicdn.com/cache/750x750/ts/product/3f/c0/da/5d8c5c5de1ee68c0dbf6f47f86855227.png', 8),
(9, 'luoc-su-thoi-gian.jpg', 1, 'https://salt.tikicdn.com/cache/750x750/ts/product/2e/de/eb/9e5f3e56bd9f4c5d9d7e30e69ac46e36.jpg', 9),
(10, 'dam-bi-ghet.jpg', 1, 'https://salt.tikicdn.com/cache/750x750/ts/product/df/7d/da/cc48567c0e60a82903d4e3ee250b3c2c.png', 10);
