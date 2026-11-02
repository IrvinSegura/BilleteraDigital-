package com.irvinsegura.wallet.accounts;

import java.util.UUID;

/**
 * Public API of the accounts module for the bank accounts users withdraw to.
 */
public interface BankAccounts {

    /**
     * For a new withdrawal: the account must belong to the user and not be removed.
     *
     * @throws BankAccountNotFoundException if it does not exist, belongs to someone else or was removed
     */
    BankAccountInfo getActiveBankAccount(UUID bankAccountId, UUID ownerId);

    /**
     * For a payout already requested: removed accounts are returned too, since the money is already on its way.
     *
     * @throws BankAccountNotFoundException if it does not exist
     */
    BankAccountInfo getBankAccount(UUID bankAccountId);
}
