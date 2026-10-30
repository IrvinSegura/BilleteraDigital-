package com.irvinsegura.wallet.accounts;

import com.irvinsegura.wallet.shared.domain.Money;
import java.util.Currency;
import java.util.Optional;
import java.util.UUID;

/**
 * Public API of the accounts module.
 */
public interface Wallets {

    /**
     * Returns the wallet only if it belongs to the given user.
     *
     * @throws WalletNotFoundException if the wallet does not exist or belongs to someone else, so callers cannot
     *                                 probe other users' wallet ids
     */
    WalletInfo getOwnedWallet(UUID walletId, UUID ownerId);

    /**
     * The user's wallet in that currency, e.g. to receive a transfer. Users have at most one per currency.
     */
    Optional<WalletInfo> findWallet(UUID ownerId, Currency currency);

    /**
     * Authorizes money leaving the wallet: locks the wallet row until the caller's transaction ends, checks that it
     * is active and within its daily limit, and counts {@code amount} as used today. Call it inside the transaction
     * that records the ledger entry, so a rollback also gives the amount back.
     *
     * @throws WalletNotFoundException     if the wallet does not exist or belongs to someone else
     * @throws WalletNotActiveException    if the wallet is frozen
     * @throws DailyLimitExceededException if the amount would exceed today's limit
     */
    WalletInfo reserveOutgoing(UUID walletId, UUID ownerId, Money amount);
}
