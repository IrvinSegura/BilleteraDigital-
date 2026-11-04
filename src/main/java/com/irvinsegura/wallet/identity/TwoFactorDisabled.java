package com.irvinsegura.wallet.identity;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when two-factor authentication is turned off, by the user or by an administrator for a lost phone.
 *
 * @param disabledBy the user themself, or the administrator's user id
 * @param reason     internal note for the audit log when an administrator did it; null otherwise
 */
public record TwoFactorDisabled(UUID userId, UUID disabledBy, String reason, Instant occurredAt) {
}
