package com.irvinsegura.wallet.transfers;

import java.time.Instant;
import java.util.UUID;

/**
 * Someone asked to take money out: it has left the wallet, and the payout is on its way. Completion or failure follow
 * as their own events.
 */
public record WithdrawalRequested(UUID withdrawalId, UUID userId, UUID walletId, long amountMinor, String currency,
        Instant occurredAt) {
}
