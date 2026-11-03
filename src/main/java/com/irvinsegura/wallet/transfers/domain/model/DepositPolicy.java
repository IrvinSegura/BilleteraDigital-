package com.irvinsegura.wallet.transfers.domain.model;

import com.irvinsegura.wallet.shared.domain.Money;
import java.util.Currency;
import java.util.Map;
import java.util.Objects;

/**
 * Caps a single deposit, per currency. Providers refuse amounts above their own maximum (Stripe: 999,999.99), and
 * a cap well below that keeps one card payment from being an outsized risk if it is later disputed.
 */
public record DepositPolicy(Map<Currency, Money> maxPerDeposit) {

    public DepositPolicy {
        maxPerDeposit = Map.copyOf(Objects.requireNonNull(maxPerDeposit, "maxPerDeposit must not be null"));
    }

    public void check(Money amount) {
        Money max = maxPerDeposit.get(amount.currency());
        // Currencies without a configured maximum are not capped
        if (max != null && !max.isGreaterThanOrEqualTo(amount)) {
            throw new DepositTooLargeException(max);
        }
    }
}
