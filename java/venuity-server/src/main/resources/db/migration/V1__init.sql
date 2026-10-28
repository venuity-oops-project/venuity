SET NAMES utf8mb4;

CREATE TABLE app_user (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_id       CHAR(36)     NOT NULL,
    username        VARCHAR(150) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    role            ENUM('ADMIN','STAFF') NOT NULL DEFAULT 'STAFF',
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    email           VARCHAR(254) NOT NULL DEFAULT '',
    first_name      VARCHAR(150) NOT NULL DEFAULT '',
    last_name       VARCHAR(150) NOT NULL DEFAULT '',
    last_login      TIMESTAMP(3) NULL,
    date_joined     TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uq_app_user_username UNIQUE (username),
    CONSTRAINT uq_app_user_public_id UNIQUE (public_id),
    INDEX idx_app_user_role (role)
) ENGINE=InnoDB;

CREATE TABLE business_settings (
    id                  TINYINT PRIMARY KEY,
    business_name       VARCHAR(150) NOT NULL,
    currency_symbol     VARCHAR(8)   NOT NULL,
    checkin_fee         DECIMAL(10,2) NOT NULL,
    primary_color       VARCHAR(7)   NOT NULL,
    primary_hover_color VARCHAR(7)   NOT NULL,
    logo_url            VARCHAR(255) NOT NULL,
    app_title           VARCHAR(150) NOT NULL,
    updated_at          TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT ck_settings_single_row CHECK (id = 1),
    CONSTRAINT ck_settings_fee_nonneg CHECK (checkin_fee >= 0)
) ENGINE=InnoDB;

CREATE TABLE customer (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_id      CHAR(36)     NOT NULL,
    public_code    VARCHAR(64)  NOT NULL,
    name           VARCHAR(150) NOT NULL,
    phone          VARCHAR(30)  NULL,
    wallet_balance DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    version        BIGINT       NOT NULL DEFAULT 0,
    created_at     TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at     TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT uq_customer_public_id UNIQUE (public_id),
    CONSTRAINT uq_customer_public_code UNIQUE (public_code),
    CONSTRAINT ck_customer_balance_nonneg CHECK (wallet_balance >= 0),
    INDEX idx_customer_name (name),
    INDEX idx_customer_phone (phone),
    INDEX idx_customer_active_created (active, created_at)
) ENGINE=InnoDB;

CREATE TABLE wallet_transaction (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id   BIGINT       NOT NULL,
    type          ENUM('TOPUP','CHECKIN','ADJUSTMENT','REFUND','OPENING_BALANCE') NOT NULL,
    amount        DECIMAL(12,2) NOT NULL,
    balance_after DECIMAL(12,2) NOT NULL,
    note          VARCHAR(1000) NULL,
    created_by    BIGINT       NULL,
    created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_tx_customer FOREIGN KEY (customer_id) REFERENCES customer(id),
    CONSTRAINT fk_tx_user FOREIGN KEY (created_by) REFERENCES app_user(id),
    CONSTRAINT ck_tx_balance_after_nonneg CHECK (balance_after >= 0),
    INDEX idx_tx_customer_time (customer_id, created_at),
    INDEX idx_tx_type_time (type, created_at),
    INDEX idx_tx_created_at (created_at)
) ENGINE=InnoDB;

CREATE TABLE check_in (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id    BIGINT       NOT NULL,
    transaction_id BIGINT       NOT NULL,
    fee_charged    DECIMAL(10,2) NOT NULL,
    checked_in_at  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_checkin_customer FOREIGN KEY (customer_id) REFERENCES customer(id),
    CONSTRAINT fk_checkin_tx FOREIGN KEY (transaction_id) REFERENCES wallet_transaction(id),
    CONSTRAINT uq_checkin_tx UNIQUE (transaction_id),
    CONSTRAINT ck_checkin_fee_nonneg CHECK (fee_charged >= 0),
    INDEX idx_checkin_time (checked_in_at),
    INDEX idx_checkin_customer_time (customer_id, checked_in_at)
) ENGINE=InnoDB;

CREATE TABLE idempotency_record (
    idem_key      CHAR(36)     NOT NULL,
    endpoint      VARCHAR(100) NOT NULL,
    user_id       BIGINT       NOT NULL,
    request_hash  CHAR(64)     NOT NULL,
    response_code INT          NULL,
    response_body JSON         NULL,
    created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (idem_key, endpoint, user_id),
    CONSTRAINT fk_idem_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    INDEX idx_idem_created (created_at)
) ENGINE=InnoDB;

CREATE OR REPLACE VIEW v_daily_stats AS
SELECT
    DATE(checked_in_at) AS day,
    COUNT(*) AS visits,
    SUM(fee_charged) AS checkin_fees
FROM check_in
GROUP BY DATE(checked_in_at);
