package com.example.book_be.shared.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Repairs the Flyway schema history before every migration. This self-heals deployments
 * that previously failed mid-migration (leaving a "failed" row in flyway_schema_history)
 * without requiring manual SQL access to the database, e.g. on managed providers like Aiven.
 * repair() only rewrites history metadata; it never re-executes or undoes applied SQL.
 */
@Configuration
public class FlywayConfig {

    @Bean
    public FlywayMigrationStrategy repairBeforeMigrateStrategy() {
        return flyway -> {
            flyway.repair();
            flyway.migrate();
        };
    }
}
