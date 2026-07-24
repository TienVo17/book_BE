package com.example.book_be.shared.config;

import com.example.book_be.TestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfig.class)
class AivenPrimaryKeyMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void join_tables_have_composite_primary_keys_when_server_requires_them() {
        assertThat(primaryKeyColumns("nguoidung_quyen"))
                .containsExactly("ma_nguoi_dung", "ma_quyen");
        assertThat(primaryKeyColumns("sach_theloai"))
                .containsExactly("ma_sach", "ma_the_loai");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT @@SESSION.sql_require_primary_key", Integer.class))
                .isEqualTo(1);
    }

    private java.util.List<String> primaryKeyColumns(String tableName) {
        return jdbcTemplate.queryForList("""
                SELECT column_name
                FROM information_schema.key_column_usage
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND constraint_name = 'PRIMARY'
                ORDER BY ordinal_position
                """, String.class, tableName);
    }
}
