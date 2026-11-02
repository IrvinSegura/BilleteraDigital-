package com.irvinsegura.wallet.transfers;

import java.time.Instant;
import java.util.UUID;

/**
 * The bank refused a payout, so the money went back to the wallet.
 *
 * @param reason the payout provider's code for the refusal
 */
public record WithdrawalFailed(UUID withdrawalId, UUID userId, UUID walletId, long amountMinor, String currency,
        String reason, Instant occurredAt) {
}
