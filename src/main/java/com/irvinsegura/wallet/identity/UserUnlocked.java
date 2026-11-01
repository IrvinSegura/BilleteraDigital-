package com.irvinsegura.wallet.identity;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when an administrator lifts a lockout before it expires.
 */
public record UserUnlocked(UUID userId, UUID unlockedBy, Instant occurredAt) {
}
