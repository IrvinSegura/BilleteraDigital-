package com.irvinsegura.wallet.accounts;

import java.util.Currency;
import java.util.UUID;

/**
 * What other modules need to move money in or out of a wallet.
 *
 * @param active the status when it was read; only {@link Wallets#reserveOutgoing}, {@link Wallets#acceptIncoming}
 *               and {@link Wallets#reserveTransfer} check it under a lock, so callers must not rely on it to move
 *               money
 */
public record WalletInfo(UUID id, UUID ownerId, Currency currency, UUID ledgerAccountId, boolean active) {
}
