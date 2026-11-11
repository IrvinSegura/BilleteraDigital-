package com.irvinsegura.wallet.identity.application;

import com.irvinsegura.wallet.identity.domain.model.IdentityVerification;
import com.irvinsegura.wallet.identity.domain.model.IneCredential;
import com.irvinsegura.wallet.identity.domain.model.User;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Everything an administrator needs to review a verification: the account, the card's data decrypted, what can be
 * read from it (birth date, sex) and the automatic checks' results. The pictures are fetched one by one.
 */
public record IdentityVerificationDetail(UUID id, UUID userId, String userEmail, String accountName, String status,
        String firstNames, String firstSurname, String secondSurname, String curp, String voterKey, String cic,
        String citizenId, int expiryYear, LocalDate birthDate, String sex, boolean namesMatchCurp,
        boolean nameMatchesAccount, Instant submittedAt, Instant reviewedAt, UUID reviewedBy,
        String rejectionReason) {

    static IdentityVerificationDetail of(IdentityVerification verification, User owner, IneCredential credential) {
        return new IdentityVerificationDetail(verification.id(), verification.userId(), owner.email().value(),
                owner.fullName(), verification.status().name(), credential.firstNames(), credential.firstSurname(),
                credential.secondSurname(), credential.curp(), credential.voterKey(), credential.cic(),
                credential.citizenId(), credential.expiryYear(), credential.birthDate(),
                String.valueOf(credential.sex()), verification.namesMatchCurp(), verification.nameMatchesAccount(),
                verification.submittedAt(), verification.reviewedAt(), verification.reviewedBy(),
                verification.rejectionReason());
    }
}
