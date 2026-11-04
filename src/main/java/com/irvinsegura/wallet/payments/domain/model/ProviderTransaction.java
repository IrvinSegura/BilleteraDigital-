package com.irvinsegura.wallet.payments.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A balance transaction read from the provider and where it stands in the ledger.
 *
 * @param note       why it is unmatched, for the person who has to look at it
 * @param resolution what that person decided; null until then
 */
public record ProviderTransaction(String provider, BalanceTransaction transaction, Status status, String note,
        Instant recordedAt, Instant settledAt, Resolution resolution) {

    public ProviderTransaction {
        Objects.requireNonNull(provider, "provider must not be null");
        Objects.requireNonNull(transaction, "transaction must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }

    public enum Status {
        // Waiting to be recorded, e.g. for the webhook of the payment it belongs to
        PENDING,
        // Recorded in the ledger, or nothing new to record
        POSTED,
        // The application cannot tell what it is; a person has to
        UNMATCHED,
        // A person looked at an unmatched one and closed it, without posting anything
        RESOLVED
    }

    public record Resolution(UUID resolvedBy, String reason, Instant resolvedAt) {
    }
}
