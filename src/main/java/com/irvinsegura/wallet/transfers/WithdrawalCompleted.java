package com.irvinsegura.wallet.transfers;

import java.time.Instant;
import java.util.UUID;

public record WithdrawalCompleted(UUID withdrawalId, UUID userId, UUID walletId, long amountMinor, String currency,
        Instant occurredAt) {
}
