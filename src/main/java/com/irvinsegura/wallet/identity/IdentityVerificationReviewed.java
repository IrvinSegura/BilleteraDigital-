package com.irvinsegura.wallet.identity;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when an administrator approves or rejects an identity verification.
 *
 * @param status     {@code VERIFIED} or {@code REJECTED}
 * @param reviewedBy the administrator's user id
 * @param reason     why it was rejected; {@code null} when approved
 */
public record IdentityVerificationReviewed(UUID userId, UUID verificationId, String status, UUID reviewedBy,
        String reason, Instant occurredAt) {
}
