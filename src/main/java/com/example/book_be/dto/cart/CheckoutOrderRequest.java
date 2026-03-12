package com.example.book_be.dto.cart;

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
}
