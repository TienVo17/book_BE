package com.example.book_be.shared.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class V22AuthIdentityTest {
    @Test
    void migration_keys_identity_by_provider_issuer_and_subject() throws IOException {
        try (var stream = getClass().getResourceAsStream("/db/migration/V22__auth_identity.sql")) {
            assertThat(stream).isNotNull();
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();
            assertThat(sql).contains("create table `auth_identity`", "`id` bigint", "primary key (`id`)",
                    "`ma_nguoi_dung` int", "`provider`", "`issuer`", "`provider_subject`",
                    "`created_at`", "`updated_at`",
                    "unique (`provider`, `issuer`, `provider_subject`)",
                    "(`ma_nguoi_dung`)", "references `nguoi_dung` (`ma_nguoi_dung`)",
                    "engine=innodb");
        }
    }

    /**
     * Email is a provider-controlled, mutable attribute. Keying identity on it would let a
     * provider email change silently hand one account's orders to a different person.
     */
    @Test
    void migration_never_keys_identity_on_email_or_stores_provider_tokens() throws IOException {
        try (var stream = getClass().getResourceAsStream("/db/migration/V22__auth_identity.sql")) {
            assertThat(stream).isNotNull();
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();
            assertThat(sql).doesNotContain("unique (`email`)", "unique (`provider`, `email`)");
            assertThat(sql).doesNotContain("access_token", "refresh_token", "id_token");
        }
    }
}
