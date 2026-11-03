package com.irvinsegura.wallet.ledger.domain.model;

import com.irvinsegura.wallet.ledger.InsufficientFundsException;
import com.irvinsegura.wallet.shared.domain.CurrencyMismatchException;
import com.irvinsegura.wallet.shared.domain.Money;
import java.time.Instant;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

/**
 * Ledger account with its running balance. The balance is a cache of the account's postings, kept in the same
 * transaction, so reads are cheap; it can never go below zero, for users or for the platform.
 */
public class LedgerAccount {

    public static final String SETTLEMENT = "SETTLEMENT";
    // Money already taken from a wallet for a payout the bank has not confirmed yet
    public static final String PAYOUTS_IN_TRANSIT = "PAYOUTS_IN_TRANSIT";
    // Money the payment provider collected and holds until it pays it out to the partner bank
    public static final String PROVIDER_CLEARING = "PROVIDER_CLEARING";
    // Money collected from payers that no wallet has received yet, or that is being refunded
    public static final String UNAPPLIED_PAYMENTS = "UNAPPLIED_PAYMENTS";
    public static final String PROVIDER_FEES = "PROVIDER_FEES";
    public static final String PLATFORM_CAPITAL = "PLATFORM_CAPITAL";

    private final UUID id;
    private final AccountType type;
    private final Currency currency;
    private final String systemCode;
    private final Instant createdAt;
    private final Long version;
    private Money balance;

    private LedgerAccount(UUID id, AccountType type, Money balance, String systemCode, Instant createdAt,
            Long version) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.balance = Objects.requireNonNull(balance, "balance must not be null");
        this.currency = balance.currency();
        this.systemCode = systemCode;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.version = version;
    }

    public static LedgerAccount openUserAccount(Currency currency, Instant now) {
        return new LedgerAccount(UUID.randomUUID(), AccountType.LIABILITY, Money.zero(currency), null, now, null);
    }

    /**
     * Rebuilds an account from storage. {@code version} is the optimistic lock value; null means not persisted yet.
     */
    public static LedgerAccount restore(UUID id, AccountType type, Money balance, String systemCode,
            Instant createdAt, Long version) {
        return new LedgerAccount(id, type, balance, systemCode, createdAt, version);
    }

    /**
     * Applies one side of an entry and returns the new balance.
     */
    Money apply(Side side, Money amount) {
        if (!currency.equals(amount.currency())) {
            throw new CurrencyMismatchException(currency, amount.currency());
        }
        if (type.increasesWith(side)) {
            balance = balance.plus(amount);
        } else {
            if (!balance.isGreaterThanOrEqualTo(amount)) {
                throw new InsufficientFundsException();
            }
            balance = balance.minus(amount);
        }
        return balance;
    }

    public UUID id() {
        return id;
    }

    public AccountType type() {
        return type;
    }

    public Currency currency() {
        return currency;
    }

    public Money balance() {
        return balance;
    }

    public String systemCode() {
        return systemCode;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Long version() {
        return version;
    }
}
