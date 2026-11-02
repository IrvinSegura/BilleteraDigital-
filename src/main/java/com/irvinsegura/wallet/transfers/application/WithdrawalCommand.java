package com.irvinsegura.wallet.transfers.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * @param bankAccountId where the payout goes; required for MXN, which is paid by SPEI, and absent otherwise
 */
public record WithdrawalCommand(UUID userId, String idempotencyKey, UUID walletId, BigDecimal amount,
        UUID bankAccountId) {
}
