package com.example.book_be.yeuthich.dto;

public record WishlistItemResponse(
        int maSach,
        String tenSach,
        double giaBan,
        String hinhAnh
) {
}
