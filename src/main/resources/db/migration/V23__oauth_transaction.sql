-- Trang thai mot lan cua mot luot dang nhap/lien ket qua provider.
--
-- Server la ben giu trang thai, khong phai HttpSession: ung dung chay STATELESS va co
-- nhieu instance, nen mot authorization request bat dau o instance nay phai duoc callback
-- o instance khac xac minh duoc.
--
-- Moi gia tri bi mat deu duoc bam hoac ma hoa. `state` va `code_verifier` la bearer secret
-- trong suot vong doi cua luot dang nhap: neu luu thang thi mot lan lo database chi doc
-- cung du de chiem tai khoan. Chi luu `code_verifier` da ma hoa vi callback phai gui lai
-- dung gia tri goc cho provider, con state/nonce/browser binding chi can so sanh nen bam.
CREATE TABLE `oauth_transaction` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `state_hash` VARCHAR(64) NOT NULL,
    `provider` VARCHAR(32) NOT NULL,
    `flow_kind` VARCHAR(16) NOT NULL,
    `browser_binding_hash` VARCHAR(64) NOT NULL,
    `nonce_hash` VARCHAR(64) NULL,
    `code_verifier_encrypted` VARBINARY(512) NOT NULL,
    `redirect_uri` VARCHAR(512) NOT NULL,
    `ma_nguoi_dung_muc_tieu` INT NULL,
    `created_at` DATETIME(6) NOT NULL,
    `expires_at` DATETIME(6) NOT NULL,
    `consumed_at` DATETIME(6) NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_oauth_transaction_state` UNIQUE (`state_hash`),
    INDEX `idx_oauth_transaction_expiry` (`expires_at`),
    CONSTRAINT `fk_oauth_transaction_nguoi_dung`
        FOREIGN KEY (`ma_nguoi_dung_muc_tieu`)
        REFERENCES `nguoi_dung` (`ma_nguoi_dung`)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Ho so toi thieu cua mot nguoi dung moi dang trong qua trinh dang ky bang provider.
--
-- Callback KHONG duoc tao `nguoi_dung`. Neu tao ngay tai callback thi mot nguoi bo ngang
-- o buoc dien ho so se de lai mot tai khoan nua voi, khong dang nhap lai duoc va van
-- chiem cho username/email. Ho so nam o day cho den khi buoc hoan tat tao
-- `nguoi_dung` + quyen USER + `auth_identity` trong dung mot transaction.
CREATE TABLE `oauth_signup_intent` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `intent_hash` VARCHAR(64) NOT NULL,
    `provider` VARCHAR(32) NOT NULL,
    `issuer` VARCHAR(255) NOT NULL,
    `provider_subject` VARCHAR(255) NOT NULL,
    `email` VARCHAR(255) NULL,
    `email_verified` BOOLEAN NOT NULL DEFAULT FALSE,
    `ten_hien_thi` VARCHAR(255) NULL,
    `created_at` DATETIME(6) NOT NULL,
    `expires_at` DATETIME(6) NOT NULL,
    `consumed_at` DATETIME(6) NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_oauth_signup_intent` UNIQUE (`intent_hash`),
    INDEX `idx_oauth_signup_intent_expiry` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
