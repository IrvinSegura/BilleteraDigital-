package com.irvinsegura.wallet.payments;

import java.time.Instant;
import java.util.UUID;

/**
 * An administrator closed a provider movement the application could not match. Nothing was posted to the ledger.
 */
public record ProviderTransactionResolved(String provider, String transactionId, UUID resolvedBy, String reason,
        Instant occurredAt) {
}
