package com.irvinsegura.wallet.notifications.infrastructure.scheduling;

import com.irvinsegura.wallet.notifications.application.NotificationCleanupService;
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
class NotificationCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(NotificationCleanupJob.class);

    private final NotificationCleanupService cleanupService;
    private final Duration retention;

    NotificationCleanupJob(NotificationCleanupService cleanupService,
            @Value("${wallet.notifications.cleanup.retention}") Duration retention) {
        this.cleanupService = cleanupService;
        this.retention = retention;
    }

    @Scheduled(cron = "${wallet.notifications.cleanup.cron}")
    void deleteOldNotifications() {
        int deleted = cleanupService.deleteOlderThan(retention);
        log.info("Deleted {} notifications older than {}", deleted, retention);
    }
}
