package com.example.book_be.nguoidung.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FacebookIdentityVerifierTest {
    private static final String APP_ID = "1234567890";

    private FacebookIdentityVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new FacebookIdentityVerifier(APP_ID);
    }

    private Map<String, Object> validProfile() {
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", "fb-user-1");
        profile.put("name", "Reader");
        profile.put("email", "reader@example.com");
        return profile;
    }

    private Map<String, Object> validDebug() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("app_id", APP_ID);
        data.put("is_valid", true);
        data.put("user_id", "fb-user-1");
        return Map.of("data", data);
    }

    @Test
    void a_valid_profile_and_token_produce_a_provider_identity() {
        ProviderIdentity identity = verifier.verify(validProfile(), validDebug());

        assertThat(identity.provider()).isEqualTo("facebook");
        assertThat(identity.subject()).isEqualTo("fb-user-1");
        assertThat(identity.issuer()).isEqualTo("https://www.facebook.com");
    }

    /**
     * A token minted for another Facebook app is genuine but not ours. Without checking
     * app_id, any other app's user token would log someone into this application.
     */
    @Test
    void a_token_issued_to_another_app_is_rejected() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("app_id", "9999999999");
        data.put("is_valid", true);
        data.put("user_id", "fb-user-1");

        assertThatThrownBy(() -> verifier.verify(validProfile(), Map.of("data", data)))
                .isInstanceOf(AuthIdentityException.class)
                .hasMessageContaining("OAUTH_TOKEN_INVALID");
    }

    @Test
    void an_invalid_token_is_rejected() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("app_id", APP_ID);
        data.put("is_valid", false);
        data.put("user_id", "fb-user-1");

        assertThatThrownBy(() -> verifier.verify(validProfile(), Map.of("data", data)))
                .isInstanceOf(AuthIdentityException.class)
                .hasMessageContaining("OAUTH_TOKEN_INVALID");
    }

    /**
     * If the debugged token belongs to a different user than the profile we fetched, the two
     * calls disagree about who is signing in and the flow must stop.
     */
    @Test
    void a_profile_that_disagrees_with_the_token_subject_is_rejected() {
        Map<String, Object> profile = validProfile();
        profile.put("id", "someone-else");

        assertThatThrownBy(() -> verifier.verify(profile, validDebug()))
                .isInstanceOf(AuthIdentityException.class)
                .hasMessageContaining("OAUTH_TOKEN_INVALID");
    }

    @Test
    void a_missing_subject_is_rejected() {
        Map<String, Object> profile = validProfile();
        profile.remove("id");

        assertThatThrownBy(() -> verifier.verify(profile, validDebug()))
                .isInstanceOf(AuthIdentityException.class)
                .hasMessageContaining("OAUTH_TOKEN_INVALID");
    }

    /**
     * Facebook does not tell us whether an address is verified, and a user can decline the
     * email permission entirely. Treating it as verified would let someone claim a stranger's
     * address, so it never counts as trusted here.
     */
    @Test
    void a_facebook_email_is_never_treated_as_verified() {
        ProviderIdentity identity = verifier.verify(validProfile(), validDebug());

        assertThat(identity.email()).isEqualTo("reader@example.com");
        assertThat(identity.emailVerified()).isFalse();
        assertThat(identity.trustedEmail()).isNull();
    }

    @Test
    void a_declined_email_permission_still_yields_an_identity() {
        Map<String, Object> profile = validProfile();
        profile.remove("email");

        ProviderIdentity identity = verifier.verify(profile, validDebug());

        assertThat(identity.subject()).isEqualTo("fb-user-1");
        assertThat(identity.trustedEmail()).isNull();
    }

    @Test
    void a_malformed_debug_payload_is_rejected() {
        assertThatThrownBy(() -> verifier.verify(validProfile(), Map.of()))
                .isInstanceOf(AuthIdentityException.class)
                .hasMessageContaining("OAUTH_TOKEN_INVALID");
    }

    @Test
    void the_resulting_identity_never_carries_provider_tokens() throws Exception {
        Map<String, Object> profile = validProfile();
        profile.put("access_token", "provider-access-token");

        String serialized = new ObjectMapper().writeValueAsString(
                verifier.verify(profile, validDebug()));

        assertThat(serialized).doesNotContain("provider-access-token");
    }
}
