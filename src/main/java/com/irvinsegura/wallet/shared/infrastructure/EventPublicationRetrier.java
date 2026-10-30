package com.irvinsegura.wallet.shared.infrastructure;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.FailedEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.stereotype.Component;

/**
 * Runs again the listeners that failed (e.g. the mail server was down), instead of waiting for a restart.
 * <p>
 * Each retry waits twice as long as the previous one, up to {@code maxBackoff}, so a broken listener does not flood
 * the logs and a long outage still recovers within that time. Resubmitting first claims the publication with a
 * conditional update, so instances running this at the same time never retry the same one twice.
 */
@Component
public class EventPublicationRetrier {

    private static final int BATCH_SIZE = 100;
    // 2^6 times the initial backoff; the cap below usually applies first
    private static final int MAX_DOUBLINGS = 6;

    private final FailedEventPublications failedPublications;
    private final Duration initialBackoff;
    private final Duration maxBackoff;
    private final Clock clock;

    EventPublicationRetrier(FailedEventPublications failedPublications,
            @Value("${wallet.events.retry.initial-backoff}") Duration initialBackoff,
            @Value("${wallet.events.retry.max-backoff}") Duration maxBackoff, Clock clock) {
        this.failedPublications = failedPublications;
        this.initialBackoff = initialBackoff;
        this.maxBackoff = maxBackoff;
        this.clock = clock;
    }

    /**
     * Resubmits failed publications whose backoff has elapsed; their listeners run asynchronously.
     */
    public void resubmitDueFailures() {
        Instant now = clock.instant();
        failedPublications.resubmit(ResubmissionOptions.defaults()
                .withBatchSize(BATCH_SIZE)
                .withFilter(publication -> isDue(publication, now)));
    }

    boolean isDue(EventPublication publication, Instant now) {
        return !publication.getLastPublicationDate().plus(backoff(publication.getCompletionAttempts())).isAfter(now);
    }

    Duration backoff(int attempts) {
        Duration backoff = initialBackoff.multipliedBy(1L << Math.min(Math.max(attempts, 0), MAX_DOUBLINGS));
        return backoff.compareTo(maxBackoff) > 0 ? maxBackoff : backoff;
    }
}
