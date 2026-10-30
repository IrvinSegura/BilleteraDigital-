package com.irvinsegura.wallet.transfers.application;

import java.math.BigDecimal;
import java.util.UUID;

public record DepositCommand(UUID userId, String idempotencyKey, UUID walletId, BigDecimal amount) {
}
