package com.irvinsegura.wallet.identity;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a user ends some of their own sessions from the session list.
 *
 * @param count how many sessions were ended
 */
public record SessionsRevoked(UUID userId, int count, Instant occurredAt) {
}
