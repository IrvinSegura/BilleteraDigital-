package com.irvinsegura.wallet.identity.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A user's request to have their identity verified with a voter card, and how it was reviewed. The card's data is
 * kept encrypted ({@code encryptedData}); only fingerprints, which find a card already in use without revealing it,
 * the CURP's last four characters and the automatic checks' results are stored in clear.
 */
public final class IdentityVerification {

    public static final int MAX_REJECTION_REASON = 300;

    private final UUID id;
    private final UUID userId;
    private IdentityVerificationStatus status;
    private final String encryptedData;
    private final String curpFingerprint;
    private final String voterKeyFingerprint;
    private final String curpLastFour;
    private final boolean namesMatchCurp;
    private final boolean nameMatchesAccount;
    private final Instant submittedAt;
    private Instant reviewedAt;
    private UUID reviewedBy;
    private String rejectionReason;

    private IdentityVerification(UUID id, UUID userId, IdentityVerificationStatus status, String encryptedData,
            String curpFingerprint, String voterKeyFingerprint, String curpLastFour, boolean namesMatchCurp,
            boolean nameMatchesAccount, Instant submittedAt, Instant reviewedAt, UUID reviewedBy,
            String rejectionReason) {
        this.id = Objects.requireNonNull(id);
        this.userId = Objects.requireNonNull(userId);
        this.status = Objects.requireNonNull(status);
        this.encryptedData = Objects.requireNonNull(encryptedData);
        this.curpFingerprint = Objects.requireNonNull(curpFingerprint);
        this.voterKeyFingerprint = Objects.requireNonNull(voterKeyFingerprint);
        this.curpLastFour = Objects.requireNonNull(curpLastFour);
        this.namesMatchCurp = namesMatchCurp;
        this.nameMatchesAccount = nameMatchesAccount;
        this.submittedAt = Objects.requireNonNull(submittedAt);
        this.reviewedAt = reviewedAt;
        this.reviewedBy = reviewedBy;
        this.rejectionReason = rejectionReason;
    }

    public static IdentityVerification submit(UUID userId, IneCredential credential, String encryptedData,
            String curpFingerprint, String voterKeyFingerprint, String accountName, Instant now) {
        return new IdentityVerification(UUID.randomUUID(), userId, IdentityVerificationStatus.PENDING_REVIEW,
                encryptedData, curpFingerprint, voterKeyFingerprint, credential.curpLastFour(),
                credential.namesMatchCurp(), credential.includesName(accountName), now, null, null, null);
    }

    public static IdentityVerification restore(UUID id, UUID userId, IdentityVerificationStatus status,
            String encryptedData, String curpFingerprint, String voterKeyFingerprint, String curpLastFour,
            boolean namesMatchCurp, boolean nameMatchesAccount, Instant submittedAt, Instant reviewedAt,
            UUID reviewedBy, String rejectionReason) {
        return new IdentityVerification(id, userId, status, encryptedData, curpFingerprint, voterKeyFingerprint,
                curpLastFour, namesMatchCurp, nameMatchesAccount, submittedAt, reviewedAt, reviewedBy,
                rejectionReason);
    }

    /**
     * @throws IdentityVerificationConflictException {@code ALREADY_REVIEWED} unless it is waiting for review
     */
    public void approve(UUID adminId, Instant now) {
        review(IdentityVerificationStatus.VERIFIED, adminId, null, now);
    }

    /**
     * @param reason shown to the user, so they know what to fix before trying again
     * @throws IdentityVerificationConflictException {@code ALREADY_REVIEWED} unless it is waiting for review
     */
    public void reject(UUID adminId, String reason, Instant now) {
        String text = reason == null ? "" : reason.strip();
        if (text.isEmpty() || text.length() > MAX_REJECTION_REASON) {
            throw new IllegalArgumentException("A rejection needs a reason of up to " + MAX_REJECTION_REASON
                    + " characters");
        }
        review(IdentityVerificationStatus.REJECTED, adminId, text, now);
    }

    private void review(IdentityVerificationStatus outcome, UUID adminId, String reason, Instant now) {
        if (status != IdentityVerificationStatus.PENDING_REVIEW) {
            throw new IdentityVerificationConflictException(IdentityVerificationConflictException.ALREADY_REVIEWED);
        }
        status = outcome;
        reviewedBy = Objects.requireNonNull(adminId);
        reviewedAt = now;
        rejectionReason = reason;
    }

    /**
     * Pending or verified: either way the user can't send another one, and their name can't change.
     */
    public boolean isOpen() {
        return status != IdentityVerificationStatus.REJECTED;
    }

    public UUID id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public IdentityVerificationStatus status() {
        return status;
    }

    public String encryptedData() {
        return encryptedData;
    }

    public String curpFingerprint() {
        return curpFingerprint;
    }

    public String voterKeyFingerprint() {
        return voterKeyFingerprint;
    }

    public String curpLastFour() {
        return curpLastFour;
    }

    public boolean namesMatchCurp() {
        return namesMatchCurp;
    }

    public boolean nameMatchesAccount() {
        return nameMatchesAccount;
    }

    public Instant submittedAt() {
        return submittedAt;
    }

    public Instant reviewedAt() {
        return reviewedAt;
    }

    public UUID reviewedBy() {
        return reviewedBy;
    }

    public String rejectionReason() {
        return rejectionReason;
    }
}
