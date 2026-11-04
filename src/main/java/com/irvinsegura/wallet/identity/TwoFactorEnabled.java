package com.irvinsegura.wallet.identity;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a user turns on two-factor authentication.
 */
public record TwoFactorEnabled(UUID userId, Instant occurredAt) {
}
