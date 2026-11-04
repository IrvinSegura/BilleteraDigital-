package com.irvinsegura.wallet.transfers.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * @param bankAccountId   where the payout goes; required for MXN, which is paid by SPEI, and absent otherwise
 * @param twoFactorCode   step-up proof for accounts with two-factor on; every withdrawal needs it
 * @param currentPassword step-up proof for accounts without it
 */
public record WithdrawalCommand(UUID userId, String idempotencyKey, UUID walletId, BigDecimal amount,
        UUID bankAccountId, String twoFactorCode, String currentPassword) {

    @Override
    public String toString() {
        return "WithdrawalCommand[userId=%s, walletId=%s, amount=%s, bankAccountId=%s, credentials=***]"
                .formatted(userId, walletId, amount, bankAccountId);
    }
}
