package com.example.book_be.nguoidung.identity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class SocialReturnPathTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "/",
            "/tai-khoan/oauth/ket-qua",
            "/gio-hang",
            "/thanh-toan",
    })
    void allowlisted_internal_paths_are_returned_unchanged(String path) {
        assertThat(SocialReturnPath.sanitize(path)).isEqualTo(path);
    }

    /**
     * A callback that honours an attacker-supplied absolute URL turns this application into an
     * open redirect, which is how phishing pages borrow a trusted domain.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "https://evil.example/steal",
            "http://evil.example",
            "//evil.example",
            "https:/evil.example",
            "javascript:alert(1)",
            "data:text/html,<script>alert(1)</script>",
    })
    void absolute_and_scheme_relative_targets_fall_back_to_home(String path) {
        assertThat(SocialReturnPath.sanitize(path)).isEqualTo("/");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/../admin",
            "/%2e%2e/admin",
            "/%2E%2E%2Fadmin",
            "/quan-ly/nguoi-dung",
            "/khong-ton-tai",
    })
    void traversal_and_non_allowlisted_paths_fall_back_to_home(String path) {
        assertThat(SocialReturnPath.sanitize(path)).isEqualTo("/");
    }

    @Test
    void a_fragment_is_stripped_rather_than_forwarded() {
        assertThat(SocialReturnPath.sanitize("/gio-hang#token=abc")).isEqualTo("/gio-hang");
    }

    @Test
    void null_and_blank_fall_back_to_home() {
        assertThat(SocialReturnPath.sanitize(null)).isEqualTo("/");
        assertThat(SocialReturnPath.sanitize("   ")).isEqualTo("/");
    }

    /**
     * Backslash is treated as a path separator by several browsers, so "/\evil.example" can
     * navigate off-origin even though it looks relative.
     */
    @Test
    void backslash_targets_fall_back_to_home() {
        assertThat(SocialReturnPath.sanitize("/\\evil.example")).isEqualTo("/");
        assertThat(SocialReturnPath.sanitize("\\\\evil.example")).isEqualTo("/");
    }
}
