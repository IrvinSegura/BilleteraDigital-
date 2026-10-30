package com.irvinsegura.wallet.transfers.infrastructure.scheduling;

import com.irvinsegura.wallet.transfers.application.IdempotencyKeyCleanupService;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Deleting is idempotent, so instances running it at the same time is harmless and no lock is needed.
 */
@Component
class IdempotencyKeyCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyKeyCleanupJob.class);

    private final IdempotencyKeyCleanupService cleanupService;
    private final Duration retention;

    IdempotencyKeyCleanupJob(IdempotencyKeyCleanupService cleanupService,
            @Value("${wallet.transfers.idempotency-key-cleanup.retention}") Duration retention) {
        this.cleanupService = cleanupService;
        this.retention = retention;
    }

    @Scheduled(cron = "${wallet.transfers.idempotency-key-cleanup.cron}")
    void deleteOldKeys() {
        int deleted = cleanupService.deleteOlderThan(retention);
        log.info("Deleted {} idempotency keys older than {}", deleted, retention);
    }
}
