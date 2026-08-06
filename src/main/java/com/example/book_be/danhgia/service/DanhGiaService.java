package com.example.book_be.danhgia.service;

import com.example.book_be.danhgia.domain.SuDanhGia;
import com.example.book_be.danhgia.domain.TrangThaiDanhGia;

public interface DanhGiaService {
    SuDanhGia addReview(String nhanXet, float diemXepHang, Long maNguoiDung, Long maSach);

    SuDanhGia updateReview(Long maDanhGia, SuDanhGia danhGia, Long maNguoiDungYeuCau);

    SuDanhGia deleteReview(Long maDanhGia, Long maNguoiDungYeuCau, boolean laQuanTri);

    /** Admin an/hien danh gia. Phai di qua service de du lieu tong hop duoc tinh lai. */
    SuDanhGia doiTrangThai(Long maDanhGia, TrangThaiDanhGia trangThaiMoi);

    /** Tinh lai toan bo du lieu tong hop — duong sua chua khi da lech. */
    int tinhLaiTongHopTatCa();
}
