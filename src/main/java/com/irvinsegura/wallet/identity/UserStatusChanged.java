package com.irvinsegura.wallet.identity;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when an administrator disables or enables an account.
 *
 * @param status    the new status, {@code ACTIVE} or {@code DISABLED}
 * @param changedBy the administrator's user id
 * @param reason    internal note for the audit log; never shown to the user
 */
public record UserStatusChanged(UUID userId, String status, UUID changedBy, String reason, Instant occurredAt) {
}
