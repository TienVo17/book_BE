package com.example.book_be.yeuthich.web;

import com.example.book_be.yeuthich.dto.WishlistItemResponse;
import com.example.book_be.yeuthich.service.WishlistService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/yeu-thich")
public class YeuThichController {

    private final WishlistService wishlistService;

    public YeuThichController(WishlistService wishlistService) {
        this.wishlistService = wishlistService;
    }

    @GetMapping
    public ResponseEntity<List<WishlistItemResponse>> findAll() {
        return ResponseEntity.ok(wishlistService.getCurrentUserWishlist());
    }

    @PostMapping("/{maSach}")
    public ResponseEntity<List<WishlistItemResponse>> addToWishlist(
            @PathVariable Integer maSach
    ) {
        return ResponseEntity.ok(wishlistService.ensureBookPresent(maSach));
    }

    @DeleteMapping("/{maSach}")
    public ResponseEntity<List<WishlistItemResponse>> removeFromWishlist(
            @PathVariable Integer maSach
    ) {
        return ResponseEntity.ok(wishlistService.ensureBookAbsent(maSach));
    }
}
