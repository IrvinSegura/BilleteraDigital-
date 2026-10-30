package com.irvinsegura.wallet.shared.infrastructure;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Every instance runs it; {@link EventPublicationRetrier} makes sure each failed publication is retried once.
 */
@Component
class EventPublicationRetryJob {

    private final EventPublicationRetrier retrier;

    EventPublicationRetryJob(EventPublicationRetrier retrier) {
        this.retrier = retrier;
    }

    @Scheduled(fixedDelayString = "${wallet.events.retry.interval}")
    void resubmitFailedPublications() {
        retrier.resubmitDueFailures();
    }
}
