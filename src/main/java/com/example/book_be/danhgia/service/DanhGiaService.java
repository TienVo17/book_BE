package com.example.book_be.danhgia.service;

import com.example.book_be.danhgia.domain.SuDanhGia;
import com.example.book_be.danhgia.domain.TrangThaiDanhGia;
import com.example.book_be.danhgia.dto.CoTheDanhGiaResponse;

public interface DanhGiaService {
    SuDanhGia addReview(String nhanXet, float diemXepHang, Long maNguoiDung, Long maSach);

    /**
     * Nguoi nay co duoc danh gia cuon sach nay khong, va vi sao khong.
     *
     * <p>Cung mot ham duoc {@code addReview} goi lai truoc khi ghi. Endpoint chi la tien
     * ich cho giao dien; no khong phai cho chan.
     */
    CoTheDanhGiaResponse kiemTraCoTheDanhGia(int maNguoiDung, int maSach);

    SuDanhGia updateReview(Long maDanhGia, SuDanhGia danhGia, Long maNguoiDungYeuCau);

    SuDanhGia deleteReview(Long maDanhGia, Long maNguoiDungYeuCau, boolean laQuanTri);

    /** Admin an/hien danh gia. Phai di qua service de du lieu tong hop duoc tinh lai. */
    SuDanhGia doiTrangThai(Long maDanhGia, TrangThaiDanhGia trangThaiMoi);

    /** Tinh lai toan bo du lieu tong hop — duong sua chua khi da lech. */
    int tinhLaiTongHopTatCa();
}
