package com.irvinsegura.wallet.shared.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.FailedEventPublications;

class EventPublicationRetrierTest {

    private static final Instant NOW = Instant.parse("2026-10-30T12:00:00Z");

    private final EventPublicationRetrier retrier = new EventPublicationRetrier(mock(FailedEventPublications.class),
            Duration.ofMinutes(1), Duration.ofHours(1), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void backoffDoublesUpToTheMaximum() {
        assertThat(retrier.backoff(0)).isEqualTo(Duration.ofMinutes(1));
        assertThat(retrier.backoff(1)).isEqualTo(Duration.ofMinutes(2));
        assertThat(retrier.backoff(5)).isEqualTo(Duration.ofMinutes(32));
        assertThat(retrier.backoff(6)).isEqualTo(Duration.ofHours(1));
        assertThat(retrier.backoff(1_000)).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void aPublicationIsDueOnceItsBackoffHasElapsedSinceTheLastAttempt() {
        assertThat(retrier.isDue(publication(NOW.minusSeconds(59), 0), NOW)).isFalse();
        assertThat(retrier.isDue(publication(NOW.minusSeconds(60), 0), NOW)).isTrue();
        assertThat(retrier.isDue(publication(NOW.minus(Duration.ofMinutes(3)), 2), NOW)).isFalse();
        assertThat(retrier.isDue(publication(NOW.minus(Duration.ofMinutes(4)), 2), NOW)).isTrue();
    }

    private static EventPublication publication(Instant lastAttempt, int attempts) {
        EventPublication publication = mock(EventPublication.class);
        when(publication.getLastPublicationDate()).thenReturn(lastAttempt);
        when(publication.getCompletionAttempts()).thenReturn(attempts);
        return publication;
    }
}
