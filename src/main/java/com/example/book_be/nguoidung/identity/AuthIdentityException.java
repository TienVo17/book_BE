package com.example.book_be.nguoidung.identity;

/**
 * Loi cua luong danh tinh. `code` la ma on dinh de frontend re nhanh; thong diep hien cho
 * nguoi dung van phai chung chung, khong tiet lo tai khoan nao ton tai.
 */
public class AuthIdentityException extends RuntimeException {
    private final String code;

    public AuthIdentityException(String code) {
        super(code);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
