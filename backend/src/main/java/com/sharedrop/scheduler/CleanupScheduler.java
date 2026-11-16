package com.sharedrop.scheduler;

import com.sharedrop.security.RateLimitService;
import com.sharedrop.service.ExpirationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(CleanupScheduler.class);

    private final ExpirationService expirationService;
    private final RateLimitService rateLimitService;
    private final int batchSize;

    public CleanupScheduler(
            ExpirationService expirationService,
            RateLimitService rateLimitService,
            @Value("${app.cleanup.batch-size:100}") int batchSize) {
        this.expirationService = expirationService;
        this.rateLimitService = rateLimitService;
        this.batchSize = batchSize;
    }

    @Scheduled(cron = "${app.cleanup.cron:0 */15 * * * *}")
    public void performCleanup() {
        log.debug("Starting scheduled cleanup task...");
        try {
            int expiredCount = expirationService.expireShares();
            if (expiredCount > 0) {
                log.info("Expired {} shares whose validity elapsed", expiredCount);
            }

            int cleanedCount = expirationService.cleanupExpiredFiles(batchSize);
            if (cleanedCount > 0) {
                log.info("Purged {} expired shares and their associated storage objects", cleanedCount);
            }

            rateLimitService.purgeStaleEntries();
            log.debug("Scheduled cleanup completed successfully.");
        } catch (Exception e) {
            log.error("Error encountered during scheduled cleanup: {}", e.getMessage(), e);
        }
    }
}
