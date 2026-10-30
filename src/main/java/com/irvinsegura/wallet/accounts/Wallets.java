package com.irvinsegura.wallet.accounts;

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
}
