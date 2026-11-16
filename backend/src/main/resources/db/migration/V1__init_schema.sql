-- DropShare initial schema
-- Compatible with MySQL 8+ and H2 (MODE=MYSQL)

CREATE TABLE shares (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    public_token    VARCHAR(64)     NOT NULL,
    share_type      VARCHAR(10)     NOT NULL,
    share_status    VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    text_content    MEDIUMTEXT      NULL,
    password_hash   VARCHAR(255)    NULL,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at      TIMESTAMP       NOT NULL,
    max_downloads   INT             NOT NULL DEFAULT 100,
    download_count  INT             NOT NULL DEFAULT 0,

    CONSTRAINT uq_shares_token  UNIQUE (public_token),
    INDEX idx_shares_token      (public_token),
    INDEX idx_shares_status_exp (share_status, expires_at)
);

CREATE TABLE shared_files (
    id                BIGINT        AUTO_INCREMENT PRIMARY KEY,
    share_id          BIGINT        NOT NULL,
    original_filename VARCHAR(500)  NOT NULL,
    storage_key       VARCHAR(500)  NOT NULL,
    content_type      VARCHAR(255)  NOT NULL,
    file_size         BIGINT        NOT NULL,
    checksum          VARCHAR(128)  NULL,
    upload_status     VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    created_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_files_share FOREIGN KEY (share_id) REFERENCES shares(id) ON DELETE CASCADE,
    INDEX idx_files_share     (share_id)
);

CREATE TABLE otp_verifications (
    id              BIGINT        AUTO_INCREMENT PRIMARY KEY,
    share_id        BIGINT        NOT NULL,
    otp_hash        VARCHAR(255)  NOT NULL,
    expires_at      TIMESTAMP     NOT NULL,
    failed_attempts INT           NOT NULL DEFAULT 0,
    locked_until    TIMESTAMP     NULL,
    verified_at     TIMESTAMP     NULL,

    CONSTRAINT uq_otp_share   UNIQUE (share_id),
    CONSTRAINT fk_otp_share   FOREIGN KEY (share_id) REFERENCES shares(id) ON DELETE CASCADE,
    INDEX idx_otp_share       (share_id)
);
