package com.sharedrop.repository;

import com.sharedrop.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByShareIdOrderByCreatedAtDesc(Long shareId);
    List<AuditLog> findByShareTokenHashAndOwnerSecretHashOrderByCreatedAtDesc(String tokenHash, String ownerSecretHash);
}
