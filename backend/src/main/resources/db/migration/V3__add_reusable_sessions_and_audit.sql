ALTER TABLE shares ADD COLUMN owner_secret_hash VARCHAR(64) NULL;

CREATE TABLE shared_text_entries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    share_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    format VARCHAR(30) NOT NULL DEFAULT 'text',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_text_entries_share FOREIGN KEY (share_id) REFERENCES shares(id) ON DELETE CASCADE,
    INDEX idx_text_entries_share (share_id)
);

CREATE TABLE audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    share_id BIGINT NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_share_created (share_id, created_at)
);
