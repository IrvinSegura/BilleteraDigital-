package com.irvinsegura.wallet.payments.infrastructure.scheduling;

import com.irvinsegura.wallet.payments.application.WebhookEventCleanupService;
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
class WebhookEventCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(WebhookEventCleanupJob.class);

    private final WebhookEventCleanupService cleanupService;
    private final Duration retention;

    WebhookEventCleanupJob(WebhookEventCleanupService cleanupService,
            @Value("${wallet.payments.webhook-event-cleanup.retention}") Duration retention) {
        this.cleanupService = cleanupService;
        this.retention = retention;
    }

    @Scheduled(cron = "${wallet.payments.webhook-event-cleanup.cron}")
    void deleteOldWebhookEvents() {
        log.info("Deleted {} webhook events older than {}", cleanupService.deleteOlderThan(retention), retention);
    }
}
