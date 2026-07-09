package com.example.book_be.danhgia.service;

import com.example.book_be.danhgia.domain.SuDanhGia;

public interface DanhGiaService {
    SuDanhGia addReview(String nhanXet, float diemXepHang, Long maNguoiDung, Long maSach);

    SuDanhGia updateReview(Long maDanhGia, SuDanhGia danhGia);

    SuDanhGia deleteReview(Long maDanhGia);
}
