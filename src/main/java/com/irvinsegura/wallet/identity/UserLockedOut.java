package com.irvinsegura.wallet.identity;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when too many failed logins lock an account, a signal of possible password guessing.
 */
public record UserLockedOut(UUID userId, Instant lockedUntil, Instant occurredAt) {
}
