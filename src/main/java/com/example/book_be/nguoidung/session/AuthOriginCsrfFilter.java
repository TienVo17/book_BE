package com.example.book_be.nguoidung.session;

import com.example.book_be.shared.config.FrontendUrlProvider;
import com.example.book_be.shared.web.ApiErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

public class AuthOriginCsrfFilter extends OncePerRequestFilter {
    public static final String CSRF_HEADER = "X-CSRF-Token";
    private static final Set<String> PROTECTED_PATHS = Set.of(
            "/tai-khoan/refresh", "/tai-khoan/dang-xuat");

    private final String frontendOrigin;
    private final AuthCsrfTokenService csrfTokenService;
    private final ApiErrorWriter apiErrorWriter;
    private final boolean enabled;

    public AuthOriginCsrfFilter(FrontendUrlProvider frontendUrlProvider,
                                AuthCsrfTokenService csrfTokenService,
                                ApiErrorWriter apiErrorWriter,
                                @org.springframework.beans.factory.annotation.Value(
                                        "${app.auth.refresh-enabled:false}") boolean enabled) {
        this.frontendOrigin = frontendUrlProvider.getFrontendUrl();
        this.csrfTokenService = csrfTokenService;
        this.apiErrorWriter = apiErrorWriter;
        this.enabled = enabled;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !PROTECTED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        applyNoStoreHeaders(response);
        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!hasExactSourceOrigin(request)) {
            reject(request, response, "AUTH_ORIGIN_REJECTED");
            return;
        }
        String headerToken = request.getHeader(CSRF_HEADER);
        String cookieToken = cookieValue(request, AuthCsrfTokenService.COOKIE_NAME);
        if (headerToken == null || cookieToken == null
                || !java.security.MessageDigest.isEqual(
                        headerToken.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        cookieToken.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                || !csrfTokenService.isValid(headerToken)) {
            reject(request, response, "AUTH_CSRF_REJECTED");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean hasExactSourceOrigin(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        String referer = request.getHeader(HttpHeaders.REFERER);
        if (origin != null && !frontendOrigin.equals(origin)) {
            return false;
        }
        if (referer != null && !frontendOrigin.equals(originFromReferer(referer))) {
            return false;
        }
        return origin != null || referer != null;
    }

    private String originFromReferer(String referer) {
        try {
            URI uri = new URI(referer);
            if (uri.getScheme() == null || uri.getRawAuthority() == null || uri.getUserInfo() != null) {
                return null;
            }
            return uri.getScheme() + "://" + uri.getRawAuthority();
        } catch (URISyntaxException exception) {
            return null;
        }
    }

    private String cookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void reject(HttpServletRequest request, HttpServletResponse response,
                        String code) throws IOException {
        apiErrorWriter.write(request, response, HttpServletResponse.SC_FORBIDDEN,
                code, "Yêu cầu xác thực không hợp lệ.");
    }

    public static void applyNoStoreHeaders(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, private");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        response.setHeader("CDN-Cache-Control", "no-store");
    }
}
