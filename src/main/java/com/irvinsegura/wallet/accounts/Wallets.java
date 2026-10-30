package com.irvinsegura.wallet.accounts;

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
}
