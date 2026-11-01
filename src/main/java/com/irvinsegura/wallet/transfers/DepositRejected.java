package com.irvinsegura.wallet.transfers;

import java.time.Instant;
import java.util.UUID;

/**
 * The payment for a deposit succeeded but the wallet could no longer receive money, so nothing was credited and
 * the payment is being refunded.
 *
 * @param reason the code of the rule that refused the money, e.g. {@code WALLET_NOT_ACTIVE} or
 *               {@code ACCOUNT_DISABLED}
 */
public record DepositRejected(UUID depositId, UUID userId, UUID walletId, long amountMinor, String currency,
        String reason, Instant occurredAt) {
}
