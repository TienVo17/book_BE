package com.example.book_be.nguoidung.session;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

public class AuthNoStoreFilter extends OncePerRequestFilter {
    private static final Set<String> AUTH_SESSION_PATHS = Set.of(
            "/tai-khoan/csrf", "/tai-khoan/dang-nhap", "/tai-khoan/refresh",
            "/tai-khoan/dang-xuat", "/tai-khoan/phien");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !AUTH_SESSION_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        AuthOriginCsrfFilter.applyNoStoreHeaders(response);
        filterChain.doFilter(request, response);
    }
}
