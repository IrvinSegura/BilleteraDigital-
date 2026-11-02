package com.irvinsegura.wallet.transfers;

import java.time.Instant;
import java.util.UUID;

/**
 * The bank confirmed a payout. The money had already left the wallet when the withdrawal was requested.
 */
public record WithdrawalCompleted(UUID withdrawalId, UUID userId, UUID walletId, long amountMinor, String currency,
        Instant occurredAt) {
}
