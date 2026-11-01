package com.irvinsegura.wallet.payments.application;

import com.irvinsegura.wallet.payments.domain.port.WebhookEventRepository;
import java.time.Clock;
import java.time.Duration;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WebhookEventCleanupService {

    private final WebhookEventRepository webhookEvents;
    private final Clock clock;

    WebhookEventCleanupService(WebhookEventRepository webhookEvents, Clock clock) {
        this.webhookEvents = webhookEvents;
        this.clock = clock;
    }

    /**
     * The retention must outlast the provider's redelivery window, or a late redelivery is processed again.
     */
    @Transactional
    public int deleteOlderThan(Duration retention) {
        return webhookEvents.deleteReceivedBefore(clock.instant().minus(retention));
    }
}
