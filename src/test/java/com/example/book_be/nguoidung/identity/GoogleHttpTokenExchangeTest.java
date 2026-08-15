package com.example.book_be.nguoidung.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleHttpTokenExchangeTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private String jwtWith(Map<String, Object> claims) throws Exception {
        String header = base64("{\"alg\":\"RS256\"}");
        String payload = base64(MAPPER.writeValueAsString(claims));
        return header + "." + payload + ".signature-not-checked-here";
    }

    private String base64(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void claims_are_decoded_from_the_id_token_payload() throws Exception {
        String idToken = jwtWith(Map.of("sub", "sub-1", "email", "reader@example.com"));

        Map<String, Object> claims = GoogleHttpTokenExchange.decodeIdTokenClaims(idToken);

        assertThat(claims).containsEntry("sub", "sub-1")
                .containsEntry("email", "reader@example.com");
    }

    @Test
    void a_malformed_id_token_is_rejected() {
        assertThatThrownBy(() -> GoogleHttpTokenExchange.decodeIdTokenClaims("not-a-jwt"))
                .isInstanceOf(AuthIdentityException.class)
                .hasMessageContaining("OAUTH_TOKEN_INVALID");
        assertThatThrownBy(() -> GoogleHttpTokenExchange.decodeIdTokenClaims(null))
                .isInstanceOf(AuthIdentityException.class)
                .hasMessageContaining("OAUTH_TOKEN_INVALID");
    }

    @Test
    void a_payload_that_is_not_json_is_rejected() {
        String bogus = base64("{\"alg\":\"RS256\"}") + "." + base64("not json") + ".sig";

        assertThatThrownBy(() -> GoogleHttpTokenExchange.decodeIdTokenClaims(bogus))
                .isInstanceOf(AuthIdentityException.class)
                .hasMessageContaining("OAUTH_TOKEN_INVALID");
    }

    /**
     * The exchange body carries the client secret and the authorization code. Neither may ever
     * be rendered into a log line or an error message.
     */
    @Test
    void the_request_body_is_form_encoded_and_never_logged() {
        String body = GoogleHttpTokenExchange.buildFormBody(Map.of(
                "code", "auth code/with+chars",
                "client_secret", "s3cr3t"));

        assertThat(body).contains("code=auth+code%2Fwith%2Bchars");
        assertThat(body).contains("client_secret=s3cr3t");
    }
}
