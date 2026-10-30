package com.irvinsegura.wallet.accounts.infrastructure.scheduling;

import com.irvinsegura.wallet.accounts.application.OutgoingUsageCleanupService;
import java.time.Period;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Deleting is idempotent, so instances running it at the same time is harmless and no lock is needed.
 */
@Component
class OutgoingUsageCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(OutgoingUsageCleanupJob.class);

    private final OutgoingUsageCleanupService cleanupService;
    private final Period retention;

    OutgoingUsageCleanupJob(OutgoingUsageCleanupService cleanupService,
            @Value("${wallet.accounts.usage-cleanup.retention}") Period retention) {
        this.cleanupService = cleanupService;
        this.retention = retention;
    }

    @Scheduled(cron = "${wallet.accounts.usage-cleanup.cron}")
    void deleteOldUsage() {
        int deleted = cleanupService.deleteOlderThan(retention);
        log.info("Deleted {} daily usage rows older than {}", deleted, retention);
    }
}
