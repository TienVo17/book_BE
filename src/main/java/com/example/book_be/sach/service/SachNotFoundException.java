package com.example.book_be.sach.service;

public class SachNotFoundException extends RuntimeException {
    public SachNotFoundException(Long maSach) {
        super("Không tìm thấy sách với id: " + maSach);
    }
}
