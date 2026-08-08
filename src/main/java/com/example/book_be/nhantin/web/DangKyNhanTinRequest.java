package com.example.book_be.nhantin.web;

/** Than request cua {@code POST /api/nhan-tin/dang-ky}. */
public class DangKyNhanTinRequest {

    private String email;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
