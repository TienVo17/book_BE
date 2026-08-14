CREATE TABLE `refresh_token_session` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `selector` VARCHAR(64) NOT NULL,
    `family_id` VARCHAR(36) NOT NULL,
    `ma_nguoi_dung` INT NOT NULL,
    `secret_hash` VARCHAR(64) NOT NULL,
    `remember_me` BOOLEAN NOT NULL,
    `issued_at` DATETIME(6) NOT NULL,
    `absolute_expires_at` DATETIME(6) NOT NULL,
    `consumed_at` DATETIME(6) NULL,
    `revoked_at` DATETIME(6) NULL,
    `replaced_by_selector` VARCHAR(64) NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_refresh_token_session_selector` UNIQUE (`selector`),
    INDEX `idx_refresh_token_session_user_revoked` (`ma_nguoi_dung`, `revoked_at`),
    INDEX `idx_refresh_token_session_family_revoked` (`family_id`, `revoked_at`),
    INDEX `idx_refresh_token_session_absolute_expiry` (`absolute_expires_at`),
    CONSTRAINT `fk_refresh_token_session_nguoi_dung`
        FOREIGN KEY (`ma_nguoi_dung`)
        REFERENCES `nguoi_dung` (`ma_nguoi_dung`)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
