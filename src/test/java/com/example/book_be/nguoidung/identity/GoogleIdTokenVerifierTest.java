package com.example.book_be.nguoidung.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleIdTokenVerifierTest {
    private static final String CLIENT_ID = "client-abc.apps.googleusercontent.com";
    private static final Instant NOW = Instant.parse("2026-08-15T10:00:00Z");

    private GoogleIdTokenVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new GoogleIdTokenVerifier(CLIENT_ID, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Map<String, Object> validClaims() {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", "https://accounts.google.com");
        claims.put("aud", CLIENT_ID);
        claims.put("sub", "google-subject-1");
        claims.put("exp", NOW.plusSeconds(600).getEpochSecond());
        claims.put("iat", NOW.minusSeconds(60).getEpochSecond());
        claims.put("nonce", "expected-nonce");
        claims.put("email", "reader@example.com");
        claims.put("email_verified", true);
        claims.put("name", "Reader");
        return claims;
    }

    private ProviderIdentity verify(Map<String, Object> claims) {
        return verifier.verify(claims, "expected-nonce");
    }

    @Test
    void valid_claims_produce_a_provider_identity() {
        ProviderIdentity identity = verify(validClaims());

        assertThat(identity.provider()).isEqualTo("google");
        assertThat(identity.issuer()).isEqualTo("https://accounts.google.com");
        assertThat(identity.subject()).isEqualTo("google-subject-1");
        assertThat(identity.trustedEmail()).isEqualTo("reader@example.com");
    }

    @Test
    void the_alternate_google_issuer_is_accepted() {
        Map<String, Object> claims = validClaims();
        claims.put("iss", "accounts.google.com");

        assertThat(verify(claims).issuer()).isEqualTo("accounts.google.com");
    }

    /**
     * A token minted for a different client is a valid Google token, just not ours. Accepting
     * it would let any other Google app's token log a user into this application.
     */
    @Test
    void a_token_for_another_audience_is_rejected() {
        Map<String, Object> claims = validClaims();
        claims.put("aud", "someone-else.apps.googleusercontent.com");

        assertThatThrownBy(() -> verify(claims))
                .isInstanceOf(AuthIdentityException.class)
                .hasMessageContaining("OAUTH_TOKEN_INVALID");
    }

    @Test
    void a_token_from_an_unexpected_issuer_is_rejected() {
        Map<String, Object> claims = validClaims();
        claims.put("iss", "https://evil.example");

        assertThatThrownBy(() -> verify(claims))
                .isInstanceOf(AuthIdentityException.class)
                .hasMessageContaining("OAUTH_TOKEN_INVALID");
    }

    @Test
    void an_expired_token_is_rejected() {
        Map<String, Object> claims = validClaims();
        claims.put("exp", NOW.minusSeconds(1).getEpochSecond());

        assertThatThrownBy(() -> verify(claims))
                .isInstanceOf(AuthIdentityException.class)
                .hasMessageContaining("OAUTH_TOKEN_INVALID");
    }

    /**
     * The nonce ties this ID token to the authorization request this browser started. Without
     * checking it, a token captured from another flow can be replayed into this one.
     */
    @Test
    void a_mismatched_nonce_is_rejected() {
        Map<String, Object> claims = validClaims();
        claims.put("nonce", "some-other-nonce");

        assertThatThrownBy(() -> verify(claims))
                .isInstanceOf(AuthIdentityException.class)
                .hasMessageContaining("OAUTH_TOKEN_INVALID");
    }

    @Test
    void a_missing_subject_is_rejected() {
        Map<String, Object> claims = validClaims();
        claims.remove("sub");

        assertThatThrownBy(() -> verify(claims))
                .isInstanceOf(AuthIdentityException.class)
                .hasMessageContaining("OAUTH_TOKEN_INVALID");
    }

    /**
     * Google sends email_verified as the JSON boolean false or the string "false" depending on
     * the endpoint. Either must leave the address untrusted.
     */
    @Test
    void an_unverified_email_is_not_trusted_in_either_encoding() {
        Map<String, Object> booleanForm = validClaims();
        booleanForm.put("email_verified", false);
        assertThat(verify(booleanForm).trustedEmail()).isNull();

        Map<String, Object> stringForm = validClaims();
        stringForm.put("email_verified", "false");
        assertThat(verify(stringForm).trustedEmail()).isNull();
    }

    @Test
    void a_missing_email_verified_claim_is_not_trusted() {
        Map<String, Object> claims = validClaims();
        claims.remove("email_verified");

        assertThat(verify(claims).trustedEmail()).isNull();
    }

    /** The verified identity must never carry provider credentials into the application. */
    @Test
    void the_resulting_identity_never_carries_provider_tokens() throws Exception {
        Map<String, Object> claims = validClaims();
        claims.put("access_token", "provider-access-token");
        claims.put("refresh_token", "provider-refresh-token");

        String serialized = new ObjectMapper().writeValueAsString(verify(claims));

        assertThat(serialized).doesNotContain("provider-access-token", "provider-refresh-token");
    }
}
