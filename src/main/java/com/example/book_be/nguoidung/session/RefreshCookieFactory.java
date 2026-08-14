package com.example.book_be.nguoidung.session;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RefreshCookieFactory {
    public static final String COOKIE_NAME = "__Host-refresh";
    public static final long REMEMBER_ME_MAX_AGE_SECONDS = 2_592_000L;

    public ResponseCookie issue(String rawToken, boolean rememberMe) {
        ResponseCookie.ResponseCookieBuilder builder = base(rawToken);
        if (rememberMe) {
            builder.maxAge(Duration.ofSeconds(REMEMBER_ME_MAX_AGE_SECONDS));
        }
        return builder.build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .path("/")
                .secure(true)
                .httpOnly(true)
                .sameSite("Lax");
    }
}
