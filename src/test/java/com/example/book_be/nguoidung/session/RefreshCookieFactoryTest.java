package com.example.book_be.nguoidung.session;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshCookieFactoryTest {
    private final RefreshCookieFactory factory = new RefreshCookieFactory();

    @Test
    void unchecked_remember_me_uses_secure_host_only_session_cookie() {
        String cookie = factory.issue("selector.secret", false).toString();

        assertThat(cookie).contains("__Host-refresh=selector.secret", "Path=/", "Secure", "HttpOnly", "SameSite=Lax")
                .doesNotContain("Domain=", "Max-Age=");
    }

    @Test
    void checked_remember_me_uses_exact_thirty_day_max_age() {
        assertThat(factory.issue("selector.secret", true).toString())
                .contains("Max-Age=2592000");
    }

    @Test
    void clear_cookie_preserves_security_attributes() {
        assertThat(factory.clear().toString())
                .contains("__Host-refresh=", "Max-Age=0", "Path=/", "Secure", "HttpOnly", "SameSite=Lax")
                .doesNotContain("Domain=");
    }
}
