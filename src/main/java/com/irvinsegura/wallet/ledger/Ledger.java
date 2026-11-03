package com.irvinsegura.wallet.ledger;

import com.irvinsegura.wallet.shared.domain.Money;
import java.util.Currency;
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
     * Records a balanced entry and updates both balances. Accounts are locked in a fixed order, so concurrent
     * entries touching the same accounts never deadlock.
     *
     * @throws InsufficientFundsException if the entry would leave an account with a negative balance
     */
    RecordedEntry record(NewJournalEntry entry);

    Money balanceOf(UUID accountId);

    /**
     * Movements of an account, newest first.
     */
    MovementPage movementsOf(UUID accountId, int page, int size);
}
