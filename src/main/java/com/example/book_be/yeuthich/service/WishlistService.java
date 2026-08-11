package com.example.book_be.yeuthich.service;

import com.example.book_be.yeuthich.dto.WishlistItemResponse;

import java.util.List;

public interface WishlistService {
    List<WishlistItemResponse> getCurrentUserWishlist();

    List<WishlistItemResponse> ensureBookPresent(Integer maSach);

    List<WishlistItemResponse> ensureBookAbsent(Integer maSach);
}
