package com.example.book_be.nguoidung.session;

public class RefreshSessionException extends RuntimeException {
    private final String code;

    public RefreshSessionException(String code) {
        super(code);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
