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
            "/tai-khoan/kich-hoat",
            "/gio-hang/**"
    };

    public static final String[] PUBLIC_POST_ENDPOINS = {
            "/tai-khoan/dang-ky",
            "/tai-khoan/dang-nhap",
            "/gio-hang/them",
            "/api/danh-gia/them-danh-gia"
    };
    public static final String[] PUBLIC_PUT_ENDPOINS = {
            "/gio-hang/**"
    };
    public static final String[] PUBLIC_DELETE_ENDPOINS = {
            "/gio-hang/**",
    };


    // ADMIN
    public static final String[] ADMIN_GET_ENDPOINS = {
            "/nguoi-dung",
            "/nguoi-dung/**",
    };
    public static final String[] ADMIN_POST_ENDPOINS = {
            "/sach",
            "/sach/**",
            "/api/danh-gia/them-danh-gia"
    };
    public static final String[] ADMIN_PUT_ENDPOINS = {
            "/sach",
            "/sach/**",
    };
    public static final String[] ADMIN_DELETE_ENDPOINS = {
            "/sach",
            "/sach/**",
    };
}
