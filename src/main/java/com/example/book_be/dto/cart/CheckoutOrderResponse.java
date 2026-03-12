package com.example.book_be.dto.cart;

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
    private String maCoupon;
    private String phuongThucThanhToan;
    private Integer trangThaiThanhToan;
    private String hoTen;
    private String soDienThoai;
    private String diaChiNhanHang;
}
