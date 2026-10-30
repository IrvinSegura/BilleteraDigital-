package com.irvinsegura.wallet.accounts;

import java.util.Currency;
import java.util.UUID;

/**
 * What other modules need to move money in or out of a wallet.
 */
public record WalletInfo(UUID id, UUID ownerId, Currency currency, UUID ledgerAccountId, boolean active) {

    public void ensureActive() {
        if (!active) {
            throw new WalletNotActiveException();
        }
    }
}
