package com.irvinsegura.wallet.shared.domain;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

/**
 * Non-negative amount of money stored in the currency's minor units (cents) to avoid rounding errors.
 * Signed movements belong to ledger postings, not to this type.
 */
public record Money(long minorUnits, Currency currency) {

    public Money {
        Objects.requireNonNull(currency, "currency must not be null");
        if (minorUnits < 0) {
            throw new IllegalArgumentException("Money cannot be negative: " + minorUnits);
        }
    }

    public static Money ofMinor(long minorUnits, String currencyCode) {
        return new Money(minorUnits, Currency.getInstance(currencyCode));
    }

    /**
     * Converts a decimal amount such as 10.50 into minor units, rejecting more decimals than the currency allows.
     */
    public static Money of(BigDecimal amount, String currencyCode) {
        Currency currency = Currency.getInstance(currencyCode);
        // movePointRight + longValueExact throws instead of silently truncating extra decimals or overflowing
        long minorUnits = amount.movePointRight(currency.getDefaultFractionDigits()).longValueExact();
        return new Money(minorUnits, currency);
    }

    public static Money zero(Currency currency) {
        return new Money(0, currency);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.addExact(minorUnits, other.minorUnits), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(minorUnits - other.minorUnits, currency);
    }

    public boolean isGreaterThanOrEqualTo(Money other) {
        requireSameCurrency(other);
        return minorUnits >= other.minorUnits;
    }

    public boolean isZero() {
        return minorUnits == 0;
    }

    public BigDecimal toDecimal() {
        return BigDecimal.valueOf(minorUnits, currency.getDefaultFractionDigits());
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new CurrencyMismatchException(currency, other.currency);
        }
    }

    @Override
    public String toString() {
        return toDecimal().toPlainString() + " " + currency.getCurrencyCode();
    }
}
