package com.irvinsegura.wallet.identity.infrastructure.scheduling;

import com.irvinsegura.wallet.identity.application.OneTimeTokenCleanupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Deleting is idempotent, so instances running it at the same time is harmless and no lock is needed.
 */
@Component
class OneTimeTokenCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(OneTimeTokenCleanupJob.class);

    private final OneTimeTokenCleanupService cleanupService;

    OneTimeTokenCleanupJob(OneTimeTokenCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @Scheduled(cron = "${wallet.identity.one-time-token-cleanup.cron}")
    void deleteExpiredTokens() {
        log.info("Deleted {} expired email link tokens", cleanupService.deleteExpiredTokens());
    }
}
