package com.example.book_be.donhang.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutOrderResponse {
    private Integer maDonHang;
    private Double tongTien;
    private Double tongTienSanPham;
    private Double soTienGiam;
    /** Tach rieng khoi tongTien de man hinh hien duoc dong "Phi van chuyen" that su. */
    private Double phiVanChuyen;
    private String tenHinhThucGiaoHang;
    private String maCoupon;
    private String phuongThucThanhToan;
    private Integer trangThaiThanhToan;
    private String hoTen;
    private String soDienThoai;
    private String diaChiNhanHang;
}
