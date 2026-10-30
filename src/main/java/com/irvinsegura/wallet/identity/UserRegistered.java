package com.irvinsegura.wallet.identity;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when an account is created. Events carry ids only, never emails or other personal data.
 */
public record UserRegistered(UUID userId, String role, Instant occurredAt) {
}
