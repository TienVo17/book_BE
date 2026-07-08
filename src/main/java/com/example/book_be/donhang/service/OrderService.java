package com.example.book_be.donhang.service;

import com.example.book_be.donhang.dto.CheckoutOrderRequest;
import com.example.book_be.donhang.dto.CheckoutOrderResponse;

public interface OrderService {
    CheckoutOrderResponse saveOrUpdate(CheckoutOrderRequest request);
}
