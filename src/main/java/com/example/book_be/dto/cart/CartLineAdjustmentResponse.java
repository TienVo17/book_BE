package com.example.book_be.dto.cart;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CartLineAdjustmentResponse {
    private Integer maSach;
    private String tenSach;
    private Integer requestedSoLuong;
    private Integer appliedSoLuong;
    private String reason;
}
