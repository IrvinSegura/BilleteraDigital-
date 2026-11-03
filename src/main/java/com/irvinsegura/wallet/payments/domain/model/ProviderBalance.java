package com.irvinsegura.wallet.payments.domain.model;

import java.util.Objects;

/**
 * What the provider holds in one currency, available and pending together.
 *
 * @param amountMinor signed: a provider balance can go negative, e.g. after refunds
 */
public record ProviderBalance(String currency, long amountMinor) {

    public ProviderBalance {
        Objects.requireNonNull(currency, "currency must not be null");
    }
}
