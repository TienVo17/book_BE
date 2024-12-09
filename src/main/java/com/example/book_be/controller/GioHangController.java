package com.example.book_be.controller;

import com.example.book_be.services.cart.CartService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/gio-hang")
public class GioHangController {
    @Autowired
    private CartService cartService;
    @PostMapping("/them")
    public ResponseEntity<?> add(@RequestBody JsonNode jsonData){
        try {
            return cartService.save(jsonData);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }
    @PutMapping("/sua")
    private ResponseEntity<?> update(@RequestBody JsonNode jsonData){
        try {
            cartService.update(jsonData);
            return ResponseEntity.ok("Cập nhật thành công");
        }catch (Exception e){
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

}
