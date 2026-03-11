package com.example.book_be.dto.cart;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CartMergeResponse extends CartSummaryResponse {
    private Integer mergedCount = 0;
    private List<CartLineAdjustmentResponse> adjustedItems = new ArrayList<>();
    private List<CartLineAdjustmentResponse> removedItems = new ArrayList<>();
}
