package com.irvinsegura.wallet.accounts.domain.model;

import com.irvinsegura.wallet.accounts.DailyLimitExceededException;
import com.irvinsegura.wallet.shared.domain.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Currency;
import java.util.Map;
import java.util.Objects;

/**
 * Caps how much money can leave a wallet (withdrawals and transfers sent) per calendar day, per currency.
 * Days follow {@code zone}, so "today" matches the users' local day rather than UTC.
 */
public record DailyLimitPolicy(Map<Currency, Money> limits, ZoneId zone) {

    public DailyLimitPolicy {
        limits = Map.copyOf(Objects.requireNonNull(limits, "limits must not be null"));
        Objects.requireNonNull(zone, "zone must not be null");
    }

    public LocalDate dayOf(Instant instant) {
        return instant.atZone(zone).toLocalDate();
    }

    /**
     * @param usedToday what already left the wallet today, including nothing of {@code amount}
     */
    public void check(Money usedToday, Money amount) {
        Money limit = limits.get(amount.currency());
        // Currencies without a configured limit are not capped
        if (limit == null) {
            return;
        }
        // Usage can exceed the limit if the limit was lowered during the day
        Money remaining = limit.isGreaterThanOrEqualTo(usedToday) ? limit.minus(usedToday) : Money.zero(limit.currency());
        if (!remaining.isGreaterThanOrEqualTo(amount)) {
            throw new DailyLimitExceededException(limit, remaining);
        }
    }
}
