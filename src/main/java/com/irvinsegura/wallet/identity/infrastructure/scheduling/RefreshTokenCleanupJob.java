package com.irvinsegura.wallet.identity.infrastructure.scheduling;

import com.irvinsegura.wallet.identity.application.RefreshTokenCleanupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Deleting is idempotent, so instances running it at the same time is harmless and no lock is needed.
 */
@Component
class RefreshTokenCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupJob.class);

    private final RefreshTokenCleanupService cleanupService;

    RefreshTokenCleanupJob(RefreshTokenCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @Scheduled(cron = "${wallet.identity.refresh-token-cleanup.cron}")
    void deleteExpiredTokens() {
        log.info("Deleted {} expired refresh tokens", cleanupService.deleteExpiredTokens());
    }
}
