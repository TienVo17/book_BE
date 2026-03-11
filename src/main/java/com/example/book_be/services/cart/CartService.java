package com.example.book_be.services.cart;

import com.example.book_be.dto.cart.CartItemRequest;
import com.example.book_be.dto.cart.CartMergeRequest;
import com.example.book_be.dto.cart.CartMergeResponse;
import com.example.book_be.dto.cart.CartSummaryResponse;

public interface CartService {
    CartSummaryResponse getCurrentUserCart();

    CartSummaryResponse addItem(CartItemRequest request);

    CartSummaryResponse updateItemQuantity(Integer maSach, Integer soLuong);

    CartSummaryResponse removeItem(Integer maSach);

    CartMergeResponse mergeGuestCart(CartMergeRequest request);

    void clearCurrentUserCart();
}
