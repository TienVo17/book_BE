package com.example.book_be.donhang.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderDetailResponse {
    private Integer maDonHang;
    private Date ngayTao;
    private String hoTen;
    private String soDienThoai;
    private String diaChiNhanHang;
    private Integer trangThaiThanhToan;
    private Integer trangThaiGiaoHang;
    private String phuongThucThanhToan;
    private String tenPhuongThucThanhToan;
    private String tenHinhThucGiaoHang;
    private Double tongTienSanPham;
    private Double soTienGiam;
    private Double chiPhiGiaoHang;
    private Double chiPhiThanhToan;
    private Double tongTien;
    private List<OrderDetailLineItemResponse> danhSachChiTietDonHang;
}
