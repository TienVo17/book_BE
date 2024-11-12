package com.example.book_be.services.review;

import com.example.book_be.entity.NguoiDung;
import com.example.book_be.entity.Sach;
import com.example.book_be.entity.SuDanhGia;

public interface DanhGiaService {
    public SuDanhGia addReview(String nhanXet, float diemXepHang, Long maNguoiDung, Long maSach);
}
