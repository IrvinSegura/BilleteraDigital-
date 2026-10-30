package com.irvinsegura.wallet.transfers.application;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferCommand(UUID userId, String idempotencyKey, UUID sourceWalletId, String recipientEmail,
        BigDecimal amount, String description) {
}
