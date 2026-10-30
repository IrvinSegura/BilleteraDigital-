package com.irvinsegura.wallet.transfers;

import java.time.Instant;
import java.util.UUID;

/**
 * Amounts in events are minor units plus currency code, so the stored (serialized) event never depends on how a
 * value object happens to be shaped.
 */
public record DepositCompleted(UUID depositId, UUID userId, UUID walletId, long amountMinor, String currency,
        Instant occurredAt) {
}
