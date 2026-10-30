package com.irvinsegura.wallet.accounts;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when an administrator freezes or unfreezes a wallet.
 *
 * @param status the new status, {@code FROZEN} or {@code ACTIVE}
 */
public record WalletStatusChanged(UUID walletId, UUID ownerId, String status, UUID changedBy, String reason,
        Instant occurredAt) {
}
