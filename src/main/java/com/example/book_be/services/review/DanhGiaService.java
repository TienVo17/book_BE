package com.example.book_be.services.review;

import com.example.book_be.entity.SuDanhGia;

public interface DanhGiaService {
    SuDanhGia addReview(String nhanXet, float diemXepHang, Long maNguoiDung, Long maSach);

    SuDanhGia updateReview(Long maDanhGia, SuDanhGia danhGia);

    SuDanhGia deleteReview(Long maDanhGia);
}
