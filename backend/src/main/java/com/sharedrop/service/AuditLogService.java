package com.sharedrop.service;

import com.sharedrop.dto.response.AuditEventResponse;
import com.sharedrop.entity.AuditLog;
import com.sharedrop.entity.Share;
import com.sharedrop.exception.InvalidOtpException;
import com.sharedrop.exception.ResourceNotFoundException;
import com.sharedrop.repository.AuditLogRepository;
import com.sharedrop.repository.ShareRepository;
import com.sharedrop.util.HashUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AuditLogService {
    private final AuditLogRepository auditRepository;
    private final ShareRepository shareRepository;

    public AuditLogService(AuditLogRepository auditRepository, ShareRepository shareRepository) {
        this.auditRepository = auditRepository;
        this.shareRepository = shareRepository;
    }

    @Transactional
    public void record(Share share, String eventType, String description) {
        AuditLog event = new AuditLog();
        event.setShareId(share.getId());
        event.setEventType(eventType);
        event.setDescription(description);
        auditRepository.save(event);
    }

    @Transactional(readOnly = true)
    public List<AuditEventResponse> getForOwner(String rawToken, String ownerSecret) {
        Share share = shareRepository.findByPublicToken(HashUtil.sha256(rawToken))
                .orElseThrow(() -> new ResourceNotFoundException("Share not found"));
        if (share.getOwnerSecretHash() == null || ownerSecret == null
                || !HashUtil.sha256(ownerSecret).equals(share.getOwnerSecretHash())) {
            throw new InvalidOtpException("Sender authorization failed.");
        }
        return auditRepository.findByShareIdOrderByCreatedAtDesc(share.getId()).stream()
                .map(event -> new AuditEventResponse(event.getEventType(), event.getDescription(), event.getCreatedAt().toString()))
                .collect(Collectors.toList());
    }
}
