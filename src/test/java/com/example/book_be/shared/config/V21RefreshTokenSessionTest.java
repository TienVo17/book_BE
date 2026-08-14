package com.example.book_be.shared.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class V21RefreshTokenSessionTest {
    @Test
    void migration_defines_exact_refresh_session_storage_contract() throws IOException {
        try (var stream = getClass().getResourceAsStream(
                "/db/migration/V21__refresh_token_session.sql")) {
            assertThat(stream).isNotNull();
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();
            assertThat(sql).contains("create table `refresh_token_session`", "`id` bigint", "primary key (`id`)",
                    "`selector` varchar", "`family_id`", "`ma_nguoi_dung` int", "`secret_hash`",
                    "`remember_me`", "`issued_at`", "`absolute_expires_at`", "`consumed_at`",
                    "`revoked_at`", "`replaced_by_selector`", "unique (`selector`)",
                    "(`ma_nguoi_dung`, `revoked_at`)", "(`family_id`, `revoked_at`)",
                    "(`absolute_expires_at`)", "references `nguoi_dung` (`ma_nguoi_dung`)",
                    "engine=innodb");
            assertThat(sql).doesNotContain("raw_token");
        }
    }
}
