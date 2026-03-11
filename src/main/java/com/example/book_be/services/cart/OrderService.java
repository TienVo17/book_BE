package com.example.book_be.services.cart;

import com.example.book_be.dto.cart.CheckoutOrderRequest;
import com.example.book_be.dto.cart.CheckoutOrderResponse;

public interface OrderService {
    CheckoutOrderResponse saveOrUpdate(CheckoutOrderRequest request);
}
