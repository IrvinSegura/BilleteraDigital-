package com.irvinsegura.wallet.ledger;

import com.irvinsegura.wallet.shared.domain.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public API of the ledger module. Calls join the caller's transaction, so a business operation and its journal
 * entry are committed or rolled back together.
 */
public interface Ledger {

    /**
     * Opens a liability account holding money the platform owes a user.
     *
     * @throws UnsupportedCurrencyException if the ledger has no settlement account for the currency
     */
    UUID openUserAccount(Currency currency);

    /**
     * Account holding the platform's own funds at the partner bank, which backs deposits and withdrawals.
     *
     * @throws UnsupportedCurrencyException if the currency is not supported
     */
    UUID settlementAccount(Currency currency);

    /**
     * Liability holding money taken from wallets for payouts still on their way to the user's bank. A confirmed
     * payout moves it to the settlement account; a failed one moves it back to the wallet.
     *
     * @throws UnsupportedCurrencyException if the currency is not supported
     */
    UUID payoutsInTransitAccount(Currency currency);

    /**
     * Asset holding money a payment provider collected and has not paid out to the partner bank yet.
     *
     * @throws UnsupportedCurrencyException if the currency is not supported
     */
    UUID providerClearingAccount(Currency currency);

    /**
     * Liability holding money collected from payers that no wallet has received yet: a deposit moves it from here to
     * the wallet, a refund moves it back to where it was collected.
     *
     * @throws UnsupportedCurrencyException if the currency is not supported
     */
    UUID unappliedPaymentsAccount(Currency currency);

    /**
     * Expense recording what payment providers charge.
     *
     * @throws UnsupportedCurrencyException if the currency is not supported
     */
    UUID providerFeesAccount(Currency currency);

    /**
     * Expense recording what administrators credited to wallets to settle customers' problems.
     *
     * @throws UnsupportedCurrencyException if the currency is not supported
     */
    UUID customerAdjustmentsAccount(Currency currency);

    /**
     * Revenue recording what administrators took back from wallets, e.g. money credited twice.
     *
     * @throws UnsupportedCurrencyException if the currency is not supported
     */
    UUID adjustmentRecoveriesAccount(Currency currency);

    /**
     * Records a balanced entry and updates both balances. Accounts are locked in a fixed order, so concurrent
     * entries touching the same accounts never deadlock.
     *
     * @throws InsufficientFundsException if the entry would leave an account with a negative balance
     */
    RecordedEntry record(NewJournalEntry entry);

    Money balanceOf(UUID accountId);

    /**
     * The account's balance just before {@code at}: after its last movement posted earlier, zero before the first.
     */
    Money balanceAt(UUID accountId, Instant at);

    /**
     * Movements of an account, newest first.
     */
    MovementPage movementsOf(UUID accountId, int page, int size);

    /**
     * Movements of an account that match {@code filter}, newest first.
     */
    MovementPage movementsOf(UUID accountId, MovementFilter filter, int page, int size);

    /**
     * The movement a journal entry made on the account; empty if the entry didn't touch it.
     */
    Optional<AccountMovement> movementOf(UUID accountId, UUID journalEntryId);

    /**
     * Totals of an account's movements posted from {@code from} (inclusive) to {@code to} (exclusive), summed in
     * minor units so no cent is lost.
     */
    FlowTotals flowsOf(UUID accountId, Instant from, Instant to);

    /**
     * The account's balance at the end of each day from {@code from} to {@code to}, both included, oldest first.
     * Days are counted in {@code zone}; a day without movements closes with the previous day's balance.
     *
     * @throws IllegalArgumentException if {@code from} is after {@code to}
     */
    List<DailyBalance> dailyBalancesOf(UUID accountId, LocalDate from, LocalDate to, ZoneId zone);
}
