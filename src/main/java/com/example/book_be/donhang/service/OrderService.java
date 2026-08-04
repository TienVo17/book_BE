package com.example.book_be.donhang.service;

import com.example.book_be.donhang.dto.CheckoutOrderRequest;
import com.example.book_be.donhang.dto.CheckoutOrderResponse;

public interface OrderService {
    CheckoutOrderResponse saveOrUpdate(CheckoutOrderRequest request);

    /**
     * @param idempotencyKey khoa Idempotency-Key da normalize (trim, chieu dai/allow-list da kiem tra
     *                       o tang controller); null/blank = khong dung idempotency (hanh vi legacy).
     */
    CheckoutOrderResponse saveOrUpdate(CheckoutOrderRequest request, String idempotencyKey);
}
