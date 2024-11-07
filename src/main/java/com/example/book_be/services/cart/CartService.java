package com.example.book_be.services.cart;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;

public interface CartService {
    public ResponseEntity<?> save(JsonNode jsonData);
    public ResponseEntity<?> update(JsonNode jsonData);
}
