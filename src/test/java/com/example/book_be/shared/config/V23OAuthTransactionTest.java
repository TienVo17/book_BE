package com.example.book_be.shared.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class V23OAuthTransactionTest {
    private String sql() throws IOException {
        try (var stream = getClass().getResourceAsStream("/db/migration/V23__oauth_transaction.sql")) {
            assertThat(stream).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();
        }
    }

    @Test
    void migration_defines_one_time_authorization_transaction_storage() throws IOException {
        String sql = sql();
        assertThat(sql).contains("create table `oauth_transaction`", "`id` bigint", "primary key (`id`)",
                "`state_hash`", "`provider`", "`flow_kind`", "`browser_binding_hash`", "`nonce_hash`",
                "`code_verifier_encrypted`", "`redirect_uri`", "`ma_nguoi_dung_muc_tieu`",
                "`created_at`", "`expires_at`", "`consumed_at`",
                "unique (`state_hash`)", "(`expires_at`)", "engine=innodb");
    }

    /**
     * State and PKCE verifier are bearer secrets for the duration of the flow. Storing them
     * in the clear turns a read-only database leak into account takeover.
     */
    @Test
    void migration_stores_no_plaintext_state_verifier_or_provider_tokens() throws IOException {
        String sql = sql();
        assertThat(sql).doesNotContain("`state` varchar", "`code_verifier` varchar", "`nonce` varchar");
        assertThat(sql).doesNotContain("`access_token`", "`refresh_token`", "`id_token`");
    }

    @Test
    void migration_records_pending_social_signup_profile_for_atomic_completion() throws IOException {
        String sql = sql();
        assertThat(sql).contains("create table `oauth_signup_intent`", "`id` bigint",
                "`provider`", "`issuer`", "`provider_subject`", "`email`", "`email_verified`",
                "`ten_hien_thi`", "`created_at`", "`expires_at`", "`consumed_at`",
                "unique (`intent_hash`)", "engine=innodb");
    }
}
