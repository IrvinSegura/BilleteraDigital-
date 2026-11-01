package com.irvinsegura.wallet.payments.infrastructure.persistence;

import com.irvinsegura.wallet.payments.domain.port.WebhookEventRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Plain JDBC: "insert unless it exists" needs ON CONFLICT.
 */
@Repository
class WebhookEventJdbcAdapter implements WebhookEventRepository {

    private final JdbcClient jdbcClient;

    WebhookEventJdbcAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public boolean recordIfNew(String provider, String eventId, String type, Instant receivedAt) {
        return jdbcClient.sql("""
                        INSERT INTO payment_webhook_events (provider, event_id, type, received_at)
                        VALUES (:provider, :eventId, :type, :receivedAt)
                        ON CONFLICT (provider, event_id) DO NOTHING""")
                .param("provider", provider)
                .param("eventId", eventId)
                .param("type", type)
                .param("receivedAt", OffsetDateTime.ofInstant(receivedAt, ZoneOffset.UTC))
                .update() == 1;
    }

    @Override
    public int deleteReceivedBefore(Instant cutoff) {
        return jdbcClient.sql("DELETE FROM payment_webhook_events WHERE received_at < :cutoff")
                .param("cutoff", OffsetDateTime.ofInstant(cutoff, ZoneOffset.UTC))
                .update();
    }
}
