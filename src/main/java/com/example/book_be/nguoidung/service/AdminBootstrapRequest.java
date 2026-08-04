package com.example.book_be.nguoidung.service;

/** Gia tri bootstrap admin da duoc validate. Khong bao gio dua vao log. */
public record AdminBootstrapRequest(String tenDangNhap, String email, String matKhau) {
}
