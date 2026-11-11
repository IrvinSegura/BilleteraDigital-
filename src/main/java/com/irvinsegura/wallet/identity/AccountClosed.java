package com.irvinsegura.wallet.identity;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when owners close their own account. Every session was ended and it never signs in again.
 */
public record AccountClosed(UUID userId, Instant occurredAt) {
}
