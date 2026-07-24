package com.example.book_be.shared.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class FrontendUrlProviderTest {

    @Test
    void normalizes_trailing_slash_and_encodes_public_link_path_segments() {
        FrontendUrlProvider provider = new FrontendUrlProvider("https://frontend.example/");

        assertThat(provider.getFrontendUrl()).isEqualTo("https://frontend.example");
        assertThat(provider.activationUrl("person/name@example.com", "activation/token?value"))
                .isEqualTo("https://frontend.example/kich-hoat/person%2Fname@example.com/activation%2Ftoken%3Fvalue");
        assertThat(provider.resetPasswordUrl("person/name@example.com", "reset/token?value"))
                .isEqualTo("https://frontend.example/dat-lai-mat-khau/person%2Fname@example.com/reset%2Ftoken%3Fvalue");
    }

    @Test
    void canonicalizes_default_ports_and_ipv6_origins() {
        assertThat(new FrontendUrlProvider("https://frontend.example:443").getFrontendUrl())
                .isEqualTo("https://frontend.example");
        assertThat(new FrontendUrlProvider("http://frontend.example:80").getFrontendUrl())
                .isEqualTo("http://frontend.example");
        assertThat(new FrontendUrlProvider("https://[::1]:8443").getFrontendUrl())
                .isEqualTo("https://[::1]:8443");
    }

    @Test
    void preserves_encoded_and_unicode_return_paths() {
        assertThat(FrontendUrlProvider.normalizeReturnUrl("https://payments.example/return%20path/"))
                .isEqualTo("https://payments.example/return%20path");
        assertThat(FrontendUrlProvider.normalizeReturnUrl("https://payments.example/return%2Fpath"))
                .isEqualTo("https://payments.example/return%2Fpath");
        assertThat(FrontendUrlProvider.normalizeReturnUrl("https://payments.example/kết-quả"))
                .isEqualTo("https://payments.example/k%E1%BA%BFt-qu%E1%BA%A3");
    }

    @Test
    void rejects_frontend_urls_that_are_not_origins() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new FrontendUrlProvider("https://frontend.example/application"))
                .withMessage("app.frontend-url must not include a path");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new FrontendUrlProvider("frontend.example"))
                .withMessage("app.frontend-url must be an absolute HTTP(S) URL without credentials");
    }
}
