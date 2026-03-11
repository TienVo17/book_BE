package com.example.book_be.dto.cart;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CartSummaryResponse {
    private List<CartItemResponse> items = new ArrayList<>();
    private Integer tongSoLuong;
    private Double tongTien;
}
