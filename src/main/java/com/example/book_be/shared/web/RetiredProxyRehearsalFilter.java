package com.example.book_be.shared.web;

import com.example.book_be.nguoidung.session.AuthOriginCsrfFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class RetiredProxyRehearsalFilter extends OncePerRequestFilter {
    private static final String RETIRED_PREFIX = "/tai-khoan/_proxy-rehearsal";

    private final ApiErrorWriter errorWriter;

    public RetiredProxyRehearsalFilter(ApiErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.equals(RETIRED_PREFIX)
                && !path.startsWith(RETIRED_PREFIX + "/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        AuthOriginCsrfFilter.applyNoStoreHeaders(response);
        errorWriter.write(
                request,
                response,
                HttpStatus.NOT_FOUND.value(),
                "NOT_FOUND",
                "Không tìm thấy tài nguyên.");
    }
}
