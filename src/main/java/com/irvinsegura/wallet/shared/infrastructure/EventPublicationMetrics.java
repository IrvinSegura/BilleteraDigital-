package com.irvinsegura.wallet.shared.infrastructure;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Health of the event outbox. Finished publications are deleted, so every row left is an event whose listener has
 * not succeeded yet: a growing count or age means audit entries (and later notifications) are falling behind.
 */
@Component
class EventPublicationMetrics implements MeterBinder {

    private final JdbcClient jdbcClient;

    EventPublicationMetrics(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("wallet.events.pending", jdbcClient, client -> client.sql("""
                                SELECT count(*) FROM event_publication WHERE completion_date IS NULL""")
                        .query(Long.class)
                        .single())
                .description("Event publications whose listener has not completed")
                .register(registry);
        Gauge.builder("wallet.events.oldest.pending.age", jdbcClient, client -> client.sql("""
                                SELECT COALESCE(EXTRACT(EPOCH FROM now() - min(publication_date)), 0)
                                FROM event_publication WHERE completion_date IS NULL""")
                        .query(Double.class)
                        .single())
                .description("Age of the oldest event publication whose listener has not completed")
                .baseUnit("seconds")
                .register(registry);
    }
}
