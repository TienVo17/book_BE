package com.example.book_be.nguoidung.session;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefreshTokenCodecTest {
    private static final String TEST_KEY = "release-one-test-refresh-hmac-key-at-least-32-bytes";

    @Test
    void issues_selector_secret_and_stores_only_hmac() {
        RefreshTokenCodec codec = new RefreshTokenCodec(TEST_KEY);

        RefreshTokenCodec.IssuedToken issued = codec.issue();

        assertThat(issued.rawToken()).matches("^[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$");
        assertThat(issued.selector()).isEqualTo(issued.rawToken().substring(0, issued.rawToken().indexOf('.')));
        assertThat(issued.secretHash()).doesNotContain(issued.rawToken());
        assertThat(codec.matches(issued.rawToken(), issued.selector(), issued.secretHash())).isTrue();
    }

    @Test
    void malformed_or_modified_tokens_fail_closed() {
        RefreshTokenCodec codec = new RefreshTokenCodec(TEST_KEY);
        RefreshTokenCodec.IssuedToken issued = codec.issue();

        assertThat(codec.matches("malformed", issued.selector(), issued.secretHash())).isFalse();
        assertThat(codec.matches(issued.selector() + ".changed", issued.selector(), issued.secretHash())).isFalse();
        assertThatThrownBy(() -> new RefreshTokenCodec("short"))
                .isInstanceOf(IllegalStateException.class);
    }
}
