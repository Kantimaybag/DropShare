package com.sharedrop.service;

import com.sharedrop.entity.Share;
import com.sharedrop.entity.SharedFile;
import com.sharedrop.enums.ShareStatus;
import com.sharedrop.repository.FileRepository;
import com.sharedrop.repository.ShareRepository;
import com.sharedrop.util.HashUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class ExpirationService {

    private static final Logger log = LoggerFactory.getLogger(ExpirationService.class);

    private final ShareRepository shareRepository;
    private final FileRepository fileRepository;
    private final StorageService storageService;
    private final AuditLogService auditLogService;

    public ExpirationService(
            ShareRepository shareRepository,
            FileRepository fileRepository,
            StorageService storageService,
            AuditLogService auditLogService) {
        this.shareRepository = shareRepository;
        this.fileRepository = fileRepository;
        this.storageService = storageService;
        this.auditLogService = auditLogService;
    }

    /**
     * Marks all active shares that have passed their expiration timestamp as EXPIRED.
     *
     * @return count of newly expired shares
     */
    @Transactional
    public int expireShares() {
        List<Share> expiredShares = shareRepository.findExpiredActiveShares(Instant.now());
        if (expiredShares.isEmpty()) {
            return 0;
        }

        for (Share share : expiredShares) {
            share.setShareStatus(ShareStatus.EXPIRED);
            auditLogService.record(share, "SESSION_EXPIRED", "Session reached its expiration time.");
            shareRepository.save(share);
            log.info("Auto-expired share id={}", share.getId());
        }

        return expiredShares.size();
    }

    /**
     * Immediately destroys a session after its final allowed download has finished streaming.
     */
    @Transactional
    public void cleanupCompletedSession(String rawToken) {
        Share share = shareRepository.findByPublicToken(HashUtil.sha256(rawToken)).orElse(null);
        if (share == null || share.getShareStatus() != ShareStatus.COMPLETED) return;

        for (SharedFile file : fileRepository.findByShareId(share.getId())) {
            try {
                storageService.delete(file.getStorageKey());
            } catch (Exception e) {
                log.warn("Failed to delete completed-session file {}: {}", file.getStorageKey(), e.getMessage());
            }
        }
        auditLogService.record(share, "SESSION_DESTROYED", "Download limit reached. Session content was permanently deleted after streaming.");
        shareRepository.delete(share);
        log.info("Destroyed completed share id={} after download streaming finished", share.getId());
    }

    /**
     * Purges physical files and database records for expired, revoked, or completed shares.
     *
     * @param batchSize maximum number of shares to clean per execution batch
     * @return count of cleaned shares
     */
    @Transactional
    public int cleanupExpiredFiles(int batchSize) {
        List<Share> inactiveShares = shareRepository.findByShareStatusIn(
                List.of(ShareStatus.EXPIRED, ShareStatus.REVOKED, ShareStatus.COMPLETED)
        );

        int count = 0;
        for (Share share : inactiveShares) {
            if (count >= batchSize) {
                break;
            }

            if (share.getFiles() != null && !share.getFiles().isEmpty()) {
                for (SharedFile file : share.getFiles()) {
                    try {
                        storageService.delete(file.getStorageKey());
                    } catch (Exception e) {
                        log.warn("Failed to delete expired file {}: {}", file.getStorageKey(), e.getMessage());
                    }
                }
            }

            auditLogService.record(share, "SESSION_DESTROYED", "Session content and database records were permanently deleted.");
            // Remove database record (cascades to files, texts, and OTP; audit log is retained separately).
            shareRepository.delete(share);
            count++;
            log.debug("Cleaned up expired/revoked share id={}", share.getId());
        }

        return count;
    }
}
