package com.example.book_be.nguoidung.identity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FacebookProviderPropertiesTest {
    private static final String GOOGLE_REDIRECT =
            "https://tienvo17.vercel.app/tai-khoan/oauth/google/callback";
    private static final String FACEBOOK_REDIRECT =
            "https://tienvo17.vercel.app/tai-khoan/oauth/facebook/callback";

    private SocialProviderProperties properties(boolean facebookEnabled, String appId,
                                                String appSecret, String redirectUri) {
        return new SocialProviderProperties(
                false, "", "", "",
                facebookEnabled, appId, appSecret, redirectUri);
    }

    @Test
    void disabled_facebook_starts_without_any_credentials() {
        assertThatCode(() -> properties(false, "", "", "")).doesNotThrowAnyException();
        assertThat(properties(false, "", "", "").isFacebookEnabled()).isFalse();
    }

    @Test
    void enabled_facebook_requires_every_credential() {
        assertThatThrownBy(() -> properties(true, "", "secret", FACEBOOK_REDIRECT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FACEBOOK_CLIENT_ID");
        assertThatThrownBy(() -> properties(true, "app", "", FACEBOOK_REDIRECT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FACEBOOK_CLIENT_SECRET");
        assertThatThrownBy(() -> properties(true, "app", "secret", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FACEBOOK_REDIRECT_URI");
    }

    @Test
    void facebook_redirect_uri_must_be_absolute_https() {
        assertThatThrownBy(() -> properties(true, "app", "secret",
                "http://tienvo17.vercel.app/tai-khoan/oauth/facebook/callback"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FACEBOOK_REDIRECT_URI");
    }

    /**
     * Each provider is gated independently so Google can stay live while Facebook is still
     * being configured, and so a broken Facebook app never takes Google down with it.
     */
    @Test
    void enabling_one_provider_does_not_require_the_other() {
        SocialProviderProperties googleOnly = new SocialProviderProperties(
                true, "client", "secret", GOOGLE_REDIRECT,
                false, "", "", "");

        assertThat(googleOnly.isGoogleEnabled()).isTrue();
        assertThat(googleOnly.isFacebookEnabled()).isFalse();

        SocialProviderProperties facebookOnly = new SocialProviderProperties(
                false, "", "", "",
                true, "app", "secret", FACEBOOK_REDIRECT);

        assertThat(facebookOnly.isGoogleEnabled()).isFalse();
        assertThat(facebookOnly.isFacebookEnabled()).isTrue();
    }

    @Test
    void the_facebook_secret_never_appears_in_string_output() {
        SocialProviderProperties configured = properties(true, "app", "super-secret-app", FACEBOOK_REDIRECT);

        assertThat(configured.toString()).doesNotContain("super-secret-app");
    }
}
