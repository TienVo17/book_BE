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
 * Reproduces a real production incident: a prior deploy failed mid-migration (V1 partially
 * applied on Aiven), leaving a "failed" row in flyway_schema_history. The next deploy must
 * self-heal via FlywayConfig's repair-then-migrate strategy instead of requiring manual
 * database access.
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
    void repairs_failed_history_left_by_a_previous_broken_deploy(@TempDir Path brokenMigrationDir) throws IOException {
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

        assertThatThrownBy(realFlyway::migrate)
                .as("a dirty history must block migrate() before repair, matching the production symptom")
                .isInstanceOf(FlywayException.class);

        new FlywayConfig().repairBeforeMigrateStrategy().migrate(realFlyway);

        assertThat(realFlyway.info().current().getVersion().toString()).isEqualTo("8");
    }
}
