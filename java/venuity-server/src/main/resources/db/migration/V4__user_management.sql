SET NAMES utf8mb4;

-- 1. Extend app_user with user management, security, and audit columns
ALTER TABLE app_user
    ADD COLUMN full_name             VARCHAR(255) NOT NULL DEFAULT '',
    ADD COLUMN phone                 VARCHAR(30)  NULL DEFAULT NULL,
    ADD COLUMN notes                 TEXT         NULL,
    ADD COLUMN must_change_password  BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN password_changed_at   TIMESTAMP(3) NULL DEFAULT NULL,
    ADD COLUMN last_login_at         TIMESTAMP(3) NULL DEFAULT NULL,
    ADD COLUMN last_login_ip         VARCHAR(45)  NULL DEFAULT NULL,
    ADD COLUMN failed_login_attempts INT          NOT NULL DEFAULT 0,
    ADD COLUMN locked_until          TIMESTAMP(3) NULL DEFAULT NULL,
    ADD COLUMN account_expires_at    TIMESTAMP(3) NULL DEFAULT NULL,
    ADD COLUMN token_version         INT          NOT NULL DEFAULT 0,
    ADD COLUMN deleted_at            TIMESTAMP(3) NULL DEFAULT NULL,
    ADD COLUMN created_by            BIGINT       NULL DEFAULT NULL,
    ADD COLUMN updated_at            TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3);

-- 2. Modify email column to allow NULL, then convert empty strings to NULL and add unique constraint
ALTER TABLE app_user MODIFY COLUMN email VARCHAR(254) NULL DEFAULT NULL;
UPDATE app_user SET email = NULL WHERE email = '';
ALTER TABLE app_user ADD CONSTRAINT uq_app_user_email UNIQUE (email);

-- 3. Backfill full_name & last_login_at from legacy columns
UPDATE app_user SET full_name = TRIM(CONCAT(first_name, ' ', last_name)) WHERE (first_name != '' OR last_name != '');
UPDATE app_user SET last_login_at = last_login WHERE last_login IS NOT NULL;

-- 4. Foreign key for creator & status indexes
ALTER TABLE app_user
    ADD CONSTRAINT fk_user_created_by FOREIGN KEY (created_by) REFERENCES app_user(id) ON DELETE SET NULL,
    ADD INDEX idx_app_user_deleted_at (deleted_at),
    ADD INDEX idx_app_user_status (enabled, deleted_at, locked_until);

-- 5. Persistent user session table (replaces in-memory JWT blacklist)
CREATE TABLE user_session (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id            BIGINT       NOT NULL,
    refresh_token_hash VARCHAR(64)  NOT NULL,
    created_at         TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    last_seen_at       TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    expires_at         TIMESTAMP(3) NOT NULL,
    ip                 VARCHAR(45)  NOT NULL DEFAULT '',
    user_agent         VARCHAR(512) NOT NULL DEFAULT '',
    revoked_at         TIMESTAMP(3) NULL DEFAULT NULL,
    CONSTRAINT fk_session_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE,
    INDEX idx_session_user (user_id),
    INDEX idx_session_token_hash (refresh_token_hash),
    INDEX idx_session_expires (expires_at)
) ENGINE=InnoDB;

-- 6. Append-only audit log table
CREATE TABLE audit_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    occurred_at     TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    actor_user_id   BIGINT       NULL,
    actor_username  VARCHAR(150) NOT NULL DEFAULT 'SYSTEM',
    action          VARCHAR(64)  NOT NULL,
    target_user_id  BIGINT       NULL,
    target_username VARCHAR(150) NULL,
    ip              VARCHAR(45)  NOT NULL DEFAULT '',
    details         TEXT         NULL,
    success         BOOLEAN      NOT NULL DEFAULT TRUE,
    INDEX idx_audit_occurred (occurred_at),
    INDEX idx_audit_actor (actor_user_id),
    INDEX idx_audit_target (target_user_id),
    INDEX idx_audit_action (action)
) ENGINE=InnoDB;
