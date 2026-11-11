package com.irvinsegura.wallet.identity;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a user sends their identity document for review.
 */
public record IdentityVerificationSubmitted(UUID userId, UUID verificationId, Instant occurredAt) {
}
