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
            "/gio-hang/**",
            "/api/admin/user**",
            "/api/admin/sach**",
            "/api/sach",
            "/api/sach/**",
            "/api/sach/search",
            "/api/don-hang/them-don-hang-moi",
            // Phase 03: Product & Catalog
            "/api/the-loai",
            "/api/sach/ban-chay",
            "/api/sach/moi-nhat",
            "/api/sach/*/lien-quan",
            "/api/sach/slug/**",
            // Phase 06: SEO
            "/sitemap.xml",
            "/api/seo/**",
    };

    public static final String[] PUBLIC_POST_ENDPOINS = {
            "/tai-khoan/dang-ky",
            "/tai-khoan/dang-nhap",
            "/gio-hang/them",
            "/api/admin/sach",
            "/api/don-hang/them-don-hang-moi",
            // Phase 02: Password reset (public)
            "/tai-khoan/quen-mat-khau",
            "/tai-khoan/dat-lai-mat-khau",
    };
    public static final String[] PUBLIC_PUT_ENDPOINS = {
            "/gio-hang/**"
    };
    public static final String[] PUBLIC_DELETE_ENDPOINS = {
            "/gio-hang/**",
    };

    // AUTHENTICATED (user da dang nhap)
    public static final String[] AUTH_GET_ENDPOINTS = {
            "/api/nguoi-dung/ho-so",
            "/api/don-hang/**",
            "/api/yeu-thich",
            "/api/yeu-thich/**",
            "/api/dia-chi",
            "/api/dia-chi/**",
    };
    public static final String[] AUTH_POST_ENDPOINTS = {
            "/api/yeu-thich/**",
            "/api/dia-chi",
            "/api/coupon/kiem-tra",
    };
    public static final String[] AUTH_PUT_ENDPOINTS = {
            "/api/nguoi-dung/cap-nhat-ho-so",
            "/tai-khoan/doi-mat-khau",
            "/api/dia-chi/**",
    };
    public static final String[] AUTH_DELETE_ENDPOINTS = {
            "/api/yeu-thich/**",
            "/api/dia-chi/**",
    };

    // ADMIN
    public static final String[] ADMIN_GET_ENDPOINS = {
            "/nguoi-dung",
            "/nguoi-dung/**",
            // Phase 07: Admin Stats
            "/api/admin/thong-ke",
            "/api/admin/coupon",
            "/api/admin/coupon/**",
    };
    public static final String[] ADMIN_POST_ENDPOINS = {
            // Phase 04: Cloudinary upload
            "/api/admin/sach/*/hinh-anh",
            // Phase 05: Coupon admin
            "/api/admin/coupon",
    };
    public static final String[] ADMIN_PUT_ENDPOINS = {
            "/sach",
            "/sach/**",
            // Phase 05: Coupon admin
            "/api/admin/coupon/**",
    };
    public static final String[] ADMIN_DELETE_ENDPOINS = {
            "/sach",
            "/sach/**",
            // Phase 05: Coupon admin
            "/api/admin/coupon/**",
    };
}
