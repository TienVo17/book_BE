package com.example.book_be.giohang.service;

import com.example.book_be.giohang.dto.CartItemRequest;
import com.example.book_be.giohang.dto.CartMergeRequest;
import com.example.book_be.giohang.dto.CartMergeResponse;
import com.example.book_be.giohang.dto.CartSummaryResponse;

public interface CartService {
    CartSummaryResponse getCurrentUserCart();

    CartSummaryResponse addItem(CartItemRequest request);

    CartSummaryResponse updateItemQuantity(Integer maSach, Integer soLuong);

    CartSummaryResponse removeItem(Integer maSach);

    CartMergeResponse mergeGuestCart(CartMergeRequest request);

    void clearCurrentUserCart();
}
