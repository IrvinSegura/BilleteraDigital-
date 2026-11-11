package com.irvinsegura.wallet.identity;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when an administrator makes an account a support agent or back into a regular user.
 *
 * @param role      the new role, {@code SUPPORT} or {@code USER}
 * @param changedBy the administrator's user id
 * @param reason    internal note for the audit log; never shown to the user
 */
public record UserRoleChanged(UUID userId, String role, UUID changedBy, String reason, Instant occurredAt) {
}
