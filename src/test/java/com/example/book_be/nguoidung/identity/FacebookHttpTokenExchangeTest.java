package com.example.book_be.nguoidung.identity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FacebookHttpTokenExchangeTest {
    /**
     * appsecret_proof is an HMAC of the access token keyed by the app secret. Facebook uses it
     * to reject calls made with a stolen token from outside the app's own servers, so it must
     * be derived rather than omitted.
     */
    @Test
    void the_app_secret_proof_is_an_hmac_of_the_token() {
        String proof = FacebookHttpTokenExchange.appSecretProof("token-abc", "app-secret");

        assertThat(proof).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(proof).isNotEqualTo(
                FacebookHttpTokenExchange.appSecretProof("token-xyz", "app-secret"));
        assertThat(proof).isNotEqualTo(
                FacebookHttpTokenExchange.appSecretProof("token-abc", "other-secret"));
    }

    /** The proof must never be the token or the secret in disguise. */
    @Test
    void the_proof_never_contains_the_token_or_secret() {
        String proof = FacebookHttpTokenExchange.appSecretProof("token-abc", "app-secret");

        assertThat(proof).doesNotContain("token-abc").doesNotContain("app-secret");
    }

    @Test
    void the_pkce_verifier_travels_in_the_exchange_body() {
        String body = FacebookHttpTokenExchange.buildQuery(java.util.Map.of(
                "code", "auth code/with+chars",
                "code_verifier", "verifier-1"));

        assertThat(body).contains("code=auth+code%2Fwith%2Bchars");
        assertThat(body).contains("code_verifier=verifier-1");
    }
}
