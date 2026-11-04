package com.irvinsegura.wallet.accounts.domain.model;

import com.irvinsegura.wallet.accounts.DailyLimitExceededException;
import com.irvinsegura.wallet.shared.domain.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Currency;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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

    public Optional<Money> limitFor(Currency currency) {
        return Optional.ofNullable(limits.get(currency));
    }

    /**
     * What can still leave today; can be zero but never negative, even if the limit was lowered during the day.
     */
    public Money remaining(Money limit, Money usedToday) {
        return limit.isGreaterThanOrEqualTo(usedToday) ? limit.minus(usedToday) : Money.zero(limit.currency());
    }

    /**
     * When the day after the one {@code instant} falls on starts, in the users' zone.
     */
    public Instant nextDayStart(Instant instant) {
        return dayOf(instant).plusDays(1).atStartOfDay(zone).toInstant();
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
        Money remaining = remaining(limit, usedToday);
        if (!remaining.isGreaterThanOrEqualTo(amount)) {
            throw new DailyLimitExceededException(limit, remaining);
        }
    }
}
