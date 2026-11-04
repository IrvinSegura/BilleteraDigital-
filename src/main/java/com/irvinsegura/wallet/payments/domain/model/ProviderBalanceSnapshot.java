package com.irvinsegura.wallet.payments.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * The last comparison of what the provider holds in one currency with what the clearing account records.
 */
public record ProviderBalanceSnapshot(String provider, String currency, long providerBalanceMinor,
        long recordedBalanceMinor, Instant comparedAt) {

    public ProviderBalanceSnapshot {
        Objects.requireNonNull(provider, "provider must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        Objects.requireNonNull(comparedAt, "comparedAt must not be null");
    }

    /**
     * Zero once every movement is recorded.
     */
    public long differenceMinor() {
        return providerBalanceMinor - recordedBalanceMinor;
    }
}
