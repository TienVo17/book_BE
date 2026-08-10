package com.example.book_be.donhang.dto;

import com.example.book_be.donhang.domain.HinhThucGiaoHang;

/**
 * DTO gon cho man hinh thanh toan.
 *
 * <p>KHONG tra thang entity: {@code HinhThucGiaoHang} co quan he {@code danhSachDonHang}, va
 * serialize no se keo theo toan bo don hang cua moi khach — ten, so dien thoai, dia chi.
 */
public record HinhThucGiaoHangResponse(int maHinhThucGiaoHang,
                                       String tenHinhThucGiaoHang,
                                       String moTa,
                                       double chiPhiGiaoHang) {

    public static HinhThucGiaoHangResponse from(HinhThucGiaoHang nguon) {
        return new HinhThucGiaoHangResponse(
                nguon.getMaHinhThucGiaoHang(),
                nguon.getTenHinhThucGiaoHang(),
                nguon.getMoTa(),
                nguon.getChiPhiGiaoHang());
    }
}
