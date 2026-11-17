package com.sharedrop.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "audit_logs", indexes = @Index(name = "idx_audit_share_created", columnList = "share_id, created_at"))
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Intentionally no foreign key: audit history must survive session content deletion.
    @Column(name = "share_id", nullable = false)
    private Long shareId;

    @Column(name = "share_token_hash", nullable = false, length = 64)
    private String shareTokenHash;

    @Column(name = "owner_secret_hash", length = 64)
    private String ownerSecretHash;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "description", nullable = false, length = 1000)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() { if (createdAt == null) createdAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getShareId() { return shareId; }
    public String getShareTokenHash() { return shareTokenHash; }
    public void setShareTokenHash(String value) { this.shareTokenHash = value; }
    public String getOwnerSecretHash() { return ownerSecretHash; }
    public void setOwnerSecretHash(String value) { this.ownerSecretHash = value; }
    public void setShareId(Long shareId) { this.shareId = shareId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
