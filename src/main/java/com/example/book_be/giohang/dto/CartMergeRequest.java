package com.example.book_be.giohang.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CartMergeRequest {
    private List<CartItemRequest> items = new ArrayList<>();
}
