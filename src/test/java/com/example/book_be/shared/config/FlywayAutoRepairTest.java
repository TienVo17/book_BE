package com.example.book_be.shared.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tai hien su co that: mot lan deploy truoc do fail giua chung (V1 apply do dang tren Aiven) de lai
 * dong "failed" trong flyway_schema_history.
 *
 * Hop dong hien tai: khoi dong binh thuong PHAI fail-closed tren lich su dirty. Chi khi nguoi van
 * hanh bat repair tuong minh thi migration moi tiep tuc — de mot lan deploy hong khong the am tham
 * ghi de metadata lich su.
 */
@Testcontainers
class FlywayAutoRepairTest {

    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.0").withCommand("--sql-require-primary-key=ON");

    @BeforeAll
    static void startContainer() {
        MYSQL.start();
    }

    @AfterAll
    static void stopContainer() {
        MYSQL.stop();
    }

    @Test
    void khoi_dong_mac_dinh_fail_closed_va_chi_khoi_phuc_khi_bat_tuong_minh(
            @TempDir Path brokenMigrationDir) throws IOException {
        Files.writeString(brokenMigrationDir.resolve("V1__init_schema.sql"), "THIS IS NOT VALID SQL;");

        Flyway brokenFlyway = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("filesystem:" + brokenMigrationDir)
                .load();

        assertThatThrownBy(brokenFlyway::migrate).isInstanceOf(FlywayException.class);

        Flyway realFlyway = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .load();

        assertThatThrownBy(() -> new FlywayConfig(false).migrationStrategy().migrate(realFlyway))
                .as("mac dinh khong repair: lich su dirty phai lam deploy dung lai")
                .isInstanceOf(FlywayException.class);

        new FlywayConfig(true).migrationStrategy().migrate(realFlyway);

        assertThat(realFlyway.info().current().getVersion().toString()).isEqualTo("10");
    }
}
