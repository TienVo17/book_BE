package com.example.book_be.services.cart;

import com.example.book_be.entity.DonHang;
import com.example.book_be.entity.Sach;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface OrderService {
    DonHang saveOrUpdate(List<Sach> sachList);
}
