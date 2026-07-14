package com.example.book_be.sach.service;

public class StockAdjustmentConflictException extends RuntimeException {
    public StockAdjustmentConflictException(String message) {
        super(message);
    }
}
