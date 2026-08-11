package com.example.book_be.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class CartMergeRequestSizeFilter extends OncePerRequestFilter {
    static final int MAX_CONTENT_LENGTH_BYTES = 32 * 1024;
    private static final String MERGE_PATH = "/api/gio-hang/merge";

    private final ApiErrorWriter apiErrorWriter;

    public CartMergeRequestSizeFilter(ApiErrorWriter apiErrorWriter) {
        this.apiErrorWriter = apiErrorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod())
                || !MERGE_PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long contentLength = request.getContentLengthLong();
        if (contentLength < 0 || contentLength > MAX_CONTENT_LENGTH_BYTES) {
            apiErrorWriter.write(
                    request,
                    response,
                    HttpStatus.PAYLOAD_TOO_LARGE.value(),
                    "PAYLOAD_TOO_LARGE",
                    "Dữ liệu merge giỏ hàng vượt giới hạn cho phép.");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
