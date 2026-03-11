package com.example.book_be.dto.cart;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CartItemResponse {
    private Integer maSach;
    private String tenSach;
    private Double giaBan;
    private Integer soLuong;
    private Integer soLuongTon;
    private String hinhAnh;
    private Boolean isActive;
}
