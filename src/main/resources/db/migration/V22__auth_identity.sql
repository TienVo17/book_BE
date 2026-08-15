-- Lien ket mot tai khoan provider ben ngoai voi dung mot `nguoi_dung`.
--
-- Khoa dinh danh la (provider, issuer, provider_subject), KHONG phai email. Email do
-- provider quan ly va nguoi dung doi duoc; neu khoa theo email thi mot lan doi email
-- ben Google co the trao don hang cua tai khoan nay cho nguoi khac.
--
-- Bang nay khong luu bat ky token nao cua provider: sau khi doi lay danh tinh xong,
-- access/refresh/id token cua provider bi vut ngay trong stack cua callback.
CREATE TABLE `auth_identity` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `ma_nguoi_dung` INT NOT NULL,
    `provider` VARCHAR(32) NOT NULL,
    `issuer` VARCHAR(255) NOT NULL,
    `provider_subject` VARCHAR(255) NOT NULL,
    `created_at` DATETIME(6) NOT NULL,
    `updated_at` DATETIME(6) NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_auth_identity_provider_subject`
        UNIQUE (`provider`, `issuer`, `provider_subject`),
    INDEX `idx_auth_identity_nguoi_dung` (`ma_nguoi_dung`),
    CONSTRAINT `fk_auth_identity_nguoi_dung`
        FOREIGN KEY (`ma_nguoi_dung`)
        REFERENCES `nguoi_dung` (`ma_nguoi_dung`)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
