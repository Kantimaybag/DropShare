package com.sharedrop.entity;

import com.sharedrop.enums.ShareStatus;
import com.sharedrop.enums.ShareType;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "shares")
public class Share {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_token", nullable = false, unique = true, length = 64)
    private String publicToken;

    @Column(name = "owner_secret_hash", length = 64)
    private String ownerSecretHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "share_type", nullable = false, length = 10)
    private ShareType shareType;

    @Enumerated(EnumType.STRING)
    @Column(name = "share_status", nullable = false, length = 20)
    private ShareStatus shareStatus = ShareStatus.ACTIVE;

    @Column(name = "text_content", columnDefinition = "MEDIUMTEXT")
    private String textContent;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "max_downloads", nullable = false)
    private Integer maxDownloads = 100;

    @Column(name = "download_count", nullable = false)
    private Integer downloadCount = 0;

    @OneToMany(mappedBy = "share", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<SharedFile> files = new ArrayList<>();

    @OneToOne(mappedBy = "share", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private OtpVerification otpVerification;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    // ── Getters & Setters ────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPublicToken() { return publicToken; }
    public void setPublicToken(String publicToken) { this.publicToken = publicToken; }

    public String getOwnerSecretHash() { return ownerSecretHash; }
    public void setOwnerSecretHash(String ownerSecretHash) { this.ownerSecretHash = ownerSecretHash; }

    public ShareType getShareType() { return shareType; }
    public void setShareType(ShareType shareType) { this.shareType = shareType; }

    public ShareStatus getShareStatus() { return shareStatus; }
    public void setShareStatus(ShareStatus shareStatus) { this.shareStatus = shareStatus; }

    public String getTextContent() { return textContent; }
    public void setTextContent(String textContent) { this.textContent = textContent; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Integer getMaxDownloads() { return maxDownloads; }
    public void setMaxDownloads(Integer maxDownloads) { this.maxDownloads = maxDownloads; }

    public Integer getDownloadCount() { return downloadCount; }
    public void setDownloadCount(Integer downloadCount) { this.downloadCount = downloadCount; }

    public List<SharedFile> getFiles() { return files; }
    public void setFiles(List<SharedFile> files) { this.files = files; }

    public OtpVerification getOtpVerification() { return otpVerification; }
    public void setOtpVerification(OtpVerification otpVerification) { this.otpVerification = otpVerification; }

    public boolean isExpired() {
        return shareStatus == ShareStatus.EXPIRED || Instant.now().isAfter(expiresAt);
    }

    public boolean isActive() {
        return shareStatus == ShareStatus.ACTIVE && !Instant.now().isAfter(expiresAt);
    }

    public void incrementDownloadCount() {
        this.downloadCount++;
    }
}
