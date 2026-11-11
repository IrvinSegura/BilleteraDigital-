package com.irvinsegura.wallet.identity.application;

import com.irvinsegura.wallet.identity.domain.model.IdentityVerification;
import java.time.Instant;
import java.util.UUID;

/**
 * Where the customer's identity verification stands, as they see it: never the card's data.
 *
 * @param status          {@code NOT_STARTED}, {@code PENDING_REVIEW}, {@code VERIFIED} or {@code REJECTED}
 * @param rejectionReason what to fix, when it was rejected
 * @param attemptsLeft    verifications they can still send today
 */
public record IdentityVerificationView(UUID id, String status, String curpLastFour, Instant submittedAt,
        Instant reviewedAt, String rejectionReason, int attemptsLeft) {

    public static final String NOT_STARTED = "NOT_STARTED";

    static IdentityVerificationView of(IdentityVerification verification, int attemptsLeft) {
        return new IdentityVerificationView(verification.id(), verification.status().name(),
                verification.curpLastFour(), verification.submittedAt(), verification.reviewedAt(),
                verification.rejectionReason(), attemptsLeft);
    }

    static IdentityVerificationView notStarted(int attemptsLeft) {
        return new IdentityVerificationView(null, NOT_STARTED, null, null, null, null, attemptsLeft);
    }
}
