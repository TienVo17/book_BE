package com.example.book_be.shared.config;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Khoi dong binh thuong KHONG tu repair: mot lich su migration "dirty" phai lam deploy that bai
 * de nguoi van hanh backup, doi chieu checksum roi moi quyet dinh. Repair tu dong am tham co the
 * ghi de metadata va che giau drift giua database dang chay va migration tren classpath.
 *
 * Khi that su can khoi phuc sau mot lan deploy hong giua chung (su co Aiven truoc day), bat
 * {@code FLYWAY_REPAIR_ON_START=true} cho dung mot lan deploy, xac minh ket qua, roi tat lai.
 * {@code repair()} chi sua metadata lich su, khong rollback hay sua du lieu/schema da apply.
 */
@Configuration
public class FlywayConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(FlywayConfig.class);

    private final boolean repairOnStart;

    public FlywayConfig(@Value("${app.flyway.repair-on-start:false}") boolean repairOnStart) {
        this.repairOnStart = repairOnStart;
    }

    @Bean
    public FlywayMigrationStrategy migrationStrategy() {
        return flyway -> {
            if (repairOnStart) {
                LOGGER.warn("event=flyway_repair mode=explicit_recovery "
                        + "action_required=Tat FLYWAY_REPAIR_ON_START sau khi xac minh");
                flyway.repair();
            }
            flyway.migrate();
        };
    }

    /** Duong khoi phuc tuong minh, dung cho test va thao tac van hanh co chu dich. */
    public void khoiPhucRoiMigrate(Flyway flyway) {
        flyway.repair();
        flyway.migrate();
    }
}
