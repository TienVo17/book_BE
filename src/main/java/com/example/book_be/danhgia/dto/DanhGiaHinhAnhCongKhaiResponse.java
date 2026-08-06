package com.example.book_be.danhgia.dto;

import com.example.book_be.danhgia.domain.DanhGiaHinhAnh;

/** Anh review cong khai. Khong bao gio tra {@code cloudinaryPublicId}. */
public record DanhGiaHinhAnhCongKhaiResponse(long maHinhAnh, String urlHinh) {

    public static DanhGiaHinhAnhCongKhaiResponse from(DanhGiaHinhAnh anh) {
        return new DanhGiaHinhAnhCongKhaiResponse(anh.getMaHinhAnh(), anh.getUrlHinh());
    }
}
