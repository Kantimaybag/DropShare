-- ============================================================================
-- DropShare Production Database Schema (MySQL 8.0+)
-- ============================================================================

CREATE DATABASE IF NOT EXISTS `sharedrop`
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE `sharedrop`;

-- 1. Shares Table
CREATE TABLE IF NOT EXISTS `shares` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `public_token_hash` VARCHAR(64) NOT NULL UNIQUE,
    `share_type` VARCHAR(20) NOT NULL,
    `share_status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    `text_content` LONGTEXT,
    `snippet_format` VARCHAR(50),
    `password_hash` VARCHAR(255),
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `expires_at` TIMESTAMP NOT NULL,
    `max_downloads` INT NOT NULL DEFAULT 100,
    `download_count` INT NOT NULL DEFAULT 0,
    INDEX `idx_shares_token_hash` (`public_token_hash`),
    INDEX `idx_shares_status_expires` (`share_status`, `expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Shared Files Table
CREATE TABLE IF NOT EXISTS `shared_files` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `share_id` BIGINT NOT NULL,
    `original_filename` VARCHAR(255) NOT NULL,
    `storage_key` VARCHAR(512) NOT NULL,
    `content_type` VARCHAR(100),
    `file_size` BIGINT NOT NULL,
    `checksum` VARCHAR(64),
    `upload_status` VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_shared_files_share_id` (`share_id`),
    CONSTRAINT `fk_shared_files_share`
        FOREIGN KEY (`share_id`) REFERENCES `shares` (`id`)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. OTP Verifications Table
CREATE TABLE IF NOT EXISTS `otp_verifications` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `share_id` BIGINT NOT NULL UNIQUE,
    `otp_hash` VARCHAR(255) NOT NULL,
    `expires_at` TIMESTAMP NOT NULL,
    `failed_attempts` INT NOT NULL DEFAULT 0,
    `locked_until` TIMESTAMP NULL,
    `verified_at` TIMESTAMP NULL,
    INDEX `idx_otp_share_id` (`share_id`),
    CONSTRAINT `fk_otp_share`
        FOREIGN KEY (`share_id`) REFERENCES `shares` (`id`)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
