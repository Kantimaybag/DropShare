# DropShare • Precision Ephemeral Exchange

DropShare is a secure, developer-grade file and text-sharing platform built with **Java 21**, **Spring Boot 3**, **MySQL**, **Flyway**, and modern frontend standards. Users can exchange payloads (up to 5 GB files or sensitive plain text) with cryptographically secure one-time passwords (OTP) and configurable expiration—completely friction-free, with **no accounts, logins, or registration required**.

---

## Architecture & System Design

```
dropshare/
├── index.html                      # Linear/Stripe precision UI
├── styles.css                      # Precision transfer styles
├── app.js                          # Frontend client logic & REST API integration
├── DESIGN.md                       # Design system token specifications
└── backend/
    ├── pom.xml                     # Maven project configuration (Java 21, Spring Boot 3.2.5)
    ├── src/
    │   ├── main/
    │   │   ├── java/com/sharedrop/
    │   │   │   ├── ShareDropApplication.java
    │   │   │   ├── config/         # Security, CORS, Storage, OpenAPI configs
    │   │   │   ├── controller/     # Thin REST controllers (Share, OTP, Download, Upload, SPA)
    │   │   │   ├── service/        # Business logic (Share, OTP, Download, Storage, Expiration)
    │   │   │   ├── repository/     # Spring Data JPA repositories
    │   │   │   ├── entity/         # JPA entities (Share, SharedFile, OtpVerification)
    │   │   │   ├── dto/            # Request and Response DTOs
    │   │   │   ├── enums/          # ShareStatus, ShareType, UploadStatus
    │   │   │   ├── exception/      # GlobalExceptionHandler & domain exceptions
    │   │   │   ├── security/       # TokenGenerator, OtpGenerator, RateLimitService
    │   │   │   ├── scheduler/      # CleanupScheduler (background TTL auto-purge)
    │   │   │   └── util/           # HashUtil, FileValidator
    │   │   └── resources/
    │   │       ├── application.yml         # Production/MySQL configuration
    │   │       ├── application-dev.yml     # Zero-dependency local dev (H2 file database)
    │   │       ├── application.yml.example # Template for environment config
    │   │       └── db/migration/
    │   │           └── V1__init_schema.sql # Flyway migration script
    │   └── test/java/com/sharedrop/        # Comprehensive unit & integration tests
```

---

## Key Features

1. **Zero-Knowledge / Ephemeral Sharing**:
   - Files and texts are accessible only with the combination of the unique public link and the 6-digit OTP.
   - Sequential IDs are never exposed; high-entropy 192-bit cryptographic tokens are generated using `SecureRandom` and indexed via SHA-256 hashes.
   - OTPs are hashed using **BCrypt** (cost factor 10). Plaintext OTPs are returned only once to the sender and never stored.

2. **Large File Streaming & Low Memory Footprint**:
   - Files stream directly to disk or S3-compatible object storage via `InputStream` / `Files.copy` without loading the full file into JVM heap.
   - Configurable limits up to 5 GB per file.
   - Binary data is never stored in MySQL.

3. **Multi-File Bundles & Text Payloads**:
   - Upload single files, bundles of up to 20 files, or secret text/code snippets with syntax tags.

4. **Self-Destruction & Burn After Read**:
   - Flexible expiration windows: `10m`, `1h`, `24h`, `7d`, or `burn` (burns immediately after 1 successful download).

5. **Brute-Force & Denial-of-Service Defense**:
   - In-memory sliding-window rate limiting per IP address.
   - OTP attempt limiter: maximum 5 incorrect attempts triggers a temporary 15-minute verification lockout.
   - Path traversal sanitization prevents directory escape attacks.
   - Background cleanup scheduler purges expired files and database records periodically.

---

## Database Schema (Flyway V1)

```sql
CREATE TABLE shares (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    public_token    VARCHAR(64)     NOT NULL, -- SHA-256 hash of token
    share_type      VARCHAR(10)     NOT NULL, -- 'FILE' or 'TEXT'
    share_status    VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE', -- 'ACTIVE', 'EXPIRED', 'REVOKED', 'COMPLETED'
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
    otp_hash        VARCHAR(255)  NOT NULL, -- BCrypt hash
    expires_at      TIMESTAMP     NOT NULL,
    failed_attempts INT           NOT NULL DEFAULT 0,
    locked_until    TIMESTAMP     NULL,
    verified_at     TIMESTAMP     NULL,
    CONSTRAINT uq_otp_share   UNIQUE (share_id),
    CONSTRAINT fk_otp_share   FOREIGN KEY (share_id) REFERENCES shares(id) ON DELETE CASCADE,
    INDEX idx_otp_share       (share_id)
);
```

---

## Quickstart & Local Setup

### Prerequisites
- **Java 21** or later (`java -version`)
- **Maven 3.8+** (`mvn -version`)
- MySQL 8.0+ (optional; dev profile uses zero-dependency file-based H2 database)

### Option 1: Instant Development Mode (Zero External Dependencies)

Run with the preconfigured `dev` profile:

```powershell
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

- Backend server: `http://localhost:8080`
- DropShare UI: `http://localhost:8080/`
- Swagger OpenAPI documentation: `http://localhost:8080/swagger-ui.html`
- H2 Console: `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:file:./data/dropshare`, user: `sa`, password: `[empty]`)

### Option 2: Production Setup with MySQL

1. Create a MySQL database:
   ```sql
   CREATE DATABASE dropshare CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```

2. Export environment variables:
   ```bash
   export DB_HOST=localhost
   export DB_PORT=3306
   export DB_NAME=dropshare
   export DB_USERNAME=root
   export DB_PASSWORD=your_password
   export APP_BASE_URL=https://your-domain.com
   export STORAGE_PATH=/var/dropshare/uploads
   ```

3. Package and run:
   ```bash
   cd backend
   mvn clean package -DskipTests
   java -jar target/sharedrop-backend-1.0.0.jar
   ```

---

## REST API Reference & cURL Examples

### 1. Create a File Share
```bash
curl -X POST http://localhost:8080/api/v1/shares/file \
  -F "files=@/path/to/document.pdf" \
  -F "expiration=24h"
```
**Response (201 Created):**
```json
{
  "success": true,
  "message": "File share created successfully",
  "data": {
    "shareToken": "G2r8A_8_d6oT7q-48mK1cZ...",
    "shareUrl": "http://localhost:8080/d/G2r8A_8_d6oT7q-48mK1cZ...",
    "otp": "492018",
    "expiresAt": "2026-11-16T21:00:00Z",
    "shareType": "FILE",
    "files": [
      {
        "id": 1,
        "originalFilename": "document.pdf",
        "contentType": "application/pdf",
        "fileSize": 1048576,
        "formattedSize": "1.0 MB"
      }
    ]
  }
}
```

### 2. Create a Text Share
```bash
curl -X POST http://localhost:8080/api/v1/shares/text \
  -H "Content-Type: application/json" \
  -d '{
    "textContent": "DATABASE_URL=postgres://user:pass@db:5432/main",
    "expiration": "1h",
    "snippetFormat": "env"
  }'
```

### 3. Verify Recipient OTP
```bash
curl -X POST http://localhost:8080/api/v1/shares/G2r8A_8_d6oT7q-48mK1cZ.../verify \
  -H "Content-Type: application/json" \
  -d '{
    "otp": "492018"
  }'
```
**Response (200 OK):**
```json
{
  "success": true,
  "message": "OTP verified successfully",
  "data": {
    "shareType": "FILE",
    "files": [
      {
        "id": 1,
        "originalFilename": "document.pdf",
        "contentType": "application/pdf",
        "fileSize": 1048576,
        "formattedSize": "1.0 MB"
      }
    ],
    "textContent": null
  }
}
```

### 4. Download Protected File
*Requires prior successful OTP verification.*
```bash
curl -O -J http://localhost:8080/api/v1/shares/G2r8A_8_d6oT7q-48mK1cZ.../files/1/download
```

### 5. Check Share Status
```bash
curl http://localhost:8080/api/v1/shares/G2r8A_8_d6oT7q-48mK1cZ.../status
```

### 6. Revoke a Share
```bash
curl -X POST http://localhost:8080/api/v1/shares/G2r8A_8_d6oT7q-48mK1cZ.../revoke
```

---

## Environment Variable Reference

| Variable | Default Value | Description |
|---|---|---|
| `DB_HOST` | `localhost` | MySQL hostname |
| `DB_PORT` | `3306` | MySQL port |
| `DB_NAME` | `dropshare` | Database schema name |
| `DB_USERNAME` | `root` | Database username |
| `DB_PASSWORD` | `[empty]` | Database password |
| `APP_BASE_URL` | `http://localhost:8080` | Canonical public URL used in generated share links |
| `STORAGE_PATH` | `./uploads` | Filesystem storage path for uploaded payloads |
| `CORS_ORIGINS` | `http://localhost:3000,http://localhost:8080` | Allowed CORS origins for browser fetch |

---

## Testing

Run the test suite containing unit tests, mock validations, and integration tests:

```powershell
cd backend
mvn test
```

All 35 tests covering OTP security, brute-force lockouts, streaming downloads, burn-after-read limits, auto-cleanup, path sanitization, and REST controllers execute and pass.
