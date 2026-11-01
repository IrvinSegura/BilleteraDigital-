package com.irvinsegura.wallet.payments.domain.port;

import java.time.Instant;

/**
 * Webhook events already processed, so a redelivered one is skipped.
 */
public interface WebhookEventRepository {

    /**
     * Records the event in the caller's transaction. Returns false if it was recorded before. A concurrent delivery of
     * the same event waits for this transaction: if it commits, the other one gets false; if it rolls back, the other
     * one processes the event.
     */
    boolean recordIfNew(String provider, String eventId, String type, Instant receivedAt);

    int deleteReceivedBefore(Instant cutoff);
}
