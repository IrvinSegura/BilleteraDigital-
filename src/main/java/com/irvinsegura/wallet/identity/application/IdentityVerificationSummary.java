package com.irvinsegura.wallet.identity.application;

import com.irvinsegura.wallet.identity.domain.model.IdentityVerification;
import com.irvinsegura.wallet.identity.domain.model.User;
import java.time.Instant;
import java.util.UUID;

/**
 * One verification in the administrators' list, with the automatic checks' results so the doubtful ones stand out.
 */
public record IdentityVerificationSummary(UUID id, UUID userId, String userEmail, String userName, String status,
        String curpLastFour, boolean namesMatchCurp, boolean nameMatchesAccount, Instant submittedAt,
        Instant reviewedAt) {

    static IdentityVerificationSummary of(IdentityVerification verification, User owner) {
        return new IdentityVerificationSummary(verification.id(), verification.userId(),
                owner == null ? null : owner.email().value(), owner == null ? null : owner.fullName(),
                verification.status().name(), verification.curpLastFour(), verification.namesMatchCurp(),
                verification.nameMatchesAccount(), verification.submittedAt(), verification.reviewedAt());
    }
}
