package com.example.book_be.security;

public class Endpoints {
    public static final String front_end_host = "http://localhost:3000";
    public static final String[] PUBLIC_GET_ENDPOINS = {
            "/sach",
            "/sach/**",
            "/hinh-anh",
            "/hinh-anh/**",
            "/nguoi-dung/search/existsByTenDangNhap",
            "/nguoi-dung/search/existsByEmail",
    };

    public static final String[] PUBLIC_POST_ENDPOINS = {
            "/tai-khoan/dang-ky",
    };

    public static final String[] ADMIN_GET_ENDPOINS = {
            "/nguoi-dung",
            "/nguoi-dung/**",
    };
}
