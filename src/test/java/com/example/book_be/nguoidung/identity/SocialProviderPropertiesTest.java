package com.example.book_be.nguoidung.identity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SocialProviderPropertiesTest {
    private static final String REDIRECT = "https://tienvo17.vercel.app/tai-khoan/oauth/google/callback";

    @Test
    void disabled_google_starts_without_any_credentials() {
        assertThatCode(() -> new SocialProviderProperties(false, "", "", ""))
                .doesNotThrowAnyException();
        assertThat(new SocialProviderProperties(false, "", "", "").isGoogleEnabled()).isFalse();
    }

    /**
     * Failing at startup is the point: a missing client secret discovered mid-flow would strand
     * users on a provider consent screen that can never complete.
     */
    @Test
    void enabled_google_requires_every_credential() {
        assertThatThrownBy(() -> new SocialProviderProperties(true, "", "secret", REDIRECT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GOOGLE_CLIENT_ID");
        assertThatThrownBy(() -> new SocialProviderProperties(true, "client", "", REDIRECT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GOOGLE_CLIENT_SECRET");
        assertThatThrownBy(() -> new SocialProviderProperties(true, "client", "secret", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GOOGLE_REDIRECT_URI");
    }

    /**
     * The redirect URI is registered with Google and must be exact. An http or relative value
     * would either be rejected by Google or send an authorization code over plaintext.
     */
    @Test
    void redirect_uri_must_be_an_absolute_https_url() {
        assertThatThrownBy(() -> new SocialProviderProperties(true, "client", "secret",
                "http://tienvo17.vercel.app/tai-khoan/oauth/google/callback"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GOOGLE_REDIRECT_URI");
        assertThatThrownBy(() -> new SocialProviderProperties(true, "client", "secret",
                "/tai-khoan/oauth/google/callback"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GOOGLE_REDIRECT_URI");
    }

    @Test
    void fully_configured_google_exposes_its_settings() {
        SocialProviderProperties properties =
                new SocialProviderProperties(true, "client", "secret", REDIRECT);

        assertThat(properties.isGoogleEnabled()).isTrue();
        assertThat(properties.getGoogleClientId()).isEqualTo("client");
        assertThat(properties.getGoogleRedirectUri()).isEqualTo(REDIRECT);
    }

    /** Config values must never reach logs or error pages through a default toString. */
    @Test
    void the_secret_never_appears_in_string_output() {
        SocialProviderProperties properties =
                new SocialProviderProperties(true, "client", "super-secret-value", REDIRECT);

        assertThat(properties.toString()).doesNotContain("super-secret-value");
    }
}
