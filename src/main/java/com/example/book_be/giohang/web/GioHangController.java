package com.example.book_be.giohang.web;

import com.example.book_be.giohang.dto.CartItemRequest;
import com.example.book_be.giohang.dto.CartMergeRequest;
import com.example.book_be.giohang.dto.CartQuantityRequest;
import com.example.book_be.giohang.service.CartService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/gio-hang")
public class GioHangController {
    @Autowired
    private CartService cartService;

    @GetMapping
    public ResponseEntity<?> getCurrentUserCart() {
        return ResponseEntity.ok(cartService.getCurrentUserCart());
    }

    @PostMapping("/items")
    public ResponseEntity<?> addItem(@RequestBody CartItemRequest request) {
        return ResponseEntity.ok(cartService.addItem(request));
    }

    @PutMapping("/items/{maSach}")
    public ResponseEntity<?> updateItem(@PathVariable Integer maSach, @RequestBody CartQuantityRequest request) {
        return ResponseEntity.ok(cartService.updateItemQuantity(maSach, request.getSoLuong()));
    }

    @DeleteMapping("/items/{maSach}")
    public ResponseEntity<?> removeItem(@PathVariable Integer maSach) {
        return ResponseEntity.ok(cartService.removeItem(maSach));
    }

    @PostMapping("/merge")
    public ResponseEntity<?> mergeGuestCart(@RequestBody(required = false) CartMergeRequest request) {
        return ResponseEntity.ok(cartService.mergeGuestCart(request));
    }

    @DeleteMapping
    public ResponseEntity<?> clearCart() {
        cartService.clearCurrentUserCart();
        return ResponseEntity.ok().build();
    }
}
