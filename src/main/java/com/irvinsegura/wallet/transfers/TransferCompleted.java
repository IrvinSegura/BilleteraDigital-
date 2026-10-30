package com.irvinsegura.wallet.transfers;

import java.time.Instant;
import java.util.UUID;

public record TransferCompleted(UUID transferId, UUID senderId, UUID sourceWalletId, UUID recipientId,
        UUID destinationWalletId, long amountMinor, String currency, Instant occurredAt) {
}
