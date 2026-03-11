package com.example.book_be.dto.cart;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderListItemResponse {
    private Integer maDonHang;
    private Date ngayTao;
    private String diaChiNhanHang;
    private String phuongThucThanhToan;
    private String tenPhuongThucThanhToan;
    private Integer trangThaiThanhToan;
    private Integer trangThaiGiaoHang;
    private Double tongTien;
}
