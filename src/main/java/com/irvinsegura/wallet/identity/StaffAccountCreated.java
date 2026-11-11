package com.irvinsegura.wallet.identity;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when an administrator creates a staff account, the only way one exists besides the administrator from
 * configuration: sign-up always creates customers.
 *
 * @param role      {@code ADMIN} or {@code SUPPORT}
 * @param createdBy the administrator's user id
 * @param reason    internal note for the audit log
 */
public record StaffAccountCreated(UUID userId, String role, UUID createdBy, String reason, Instant occurredAt) {
}
