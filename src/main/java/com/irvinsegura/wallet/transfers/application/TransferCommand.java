package com.irvinsegura.wallet.transfers.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * @param twoFactorCode   step-up proof for accounts with two-factor on, when the transfer needs it
 * @param currentPassword step-up proof for accounts without it
 */
public record TransferCommand(UUID userId, String idempotencyKey, UUID sourceWalletId, String recipientEmail,
        BigDecimal amount, String description, String twoFactorCode, String currentPassword) {

    @Override
    public String toString() {
        return "TransferCommand[userId=%s, sourceWalletId=%s, amount=%s, credentials=***]"
                .formatted(userId, sourceWalletId, amount);
    }
}
