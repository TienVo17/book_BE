package com.example.book_be.donhang.dto;
import com.example.book_be.giohang.dto.CartItemRequest;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutOrderRequest {
    private List<CartItemRequest> items;
    private Integer maDiaChiGiaoHang;
    private String phuongThucThanhToan;
    private String maCoupon;
    /**
     * Co the null CO Y. Backend deploy truoc frontend, nen trong khoang giua hai lan deploy
     * se co client cu khong gui truong nay. Null giu nguyen phi 0 nhu truoc thay vi mac dinh
     * mot hinh thuc co phi — neu khong, khach bi tinh them tien cho mot lua chon ho chua he
     * nhin thay.
     */
    private Integer maHinhThucGiaoHang;
}
