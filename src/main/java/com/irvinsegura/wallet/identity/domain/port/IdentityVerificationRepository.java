package com.irvinsegura.wallet.identity.domain.port;

import com.irvinsegura.wallet.identity.domain.model.DocumentSide;
import com.irvinsegura.wallet.identity.domain.model.IdentityVerification;
import com.irvinsegura.wallet.identity.domain.model.IdentityVerificationStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface IdentityVerificationRepository {

    /**
     * Stores a new verification with its pictures, already encrypted.
     */
    void insert(IdentityVerification verification, Map<DocumentSide, byte[]> encryptedImages);

    /**
     * Stores how it was reviewed.
     */
    void updateReview(IdentityVerification verification);

    Optional<IdentityVerification> findById(UUID id);

    Optional<IdentityVerification> findByIdForUpdate(UUID id);

    /**
     * The user's most recent one, whatever its status.
     */
    Optional<IdentityVerification> findLatestByUser(UUID userId);

    int countSubmittedSince(UUID userId, Instant since);

    /**
     * Whether another user has a pending or verified verification with the same card, by its fingerprints.
     */
    boolean documentInUse(String curpFingerprint, String voterKeyFingerprint, UUID exceptUserId);

    boolean isVerified(UUID userId);

    /**
     * How many wait for an administrator's review.
     */
    long countPending();

    /**
     * Newest first, or oldest first while waiting for review so the queue is worked in order.
     */
    VerificationSlice page(IdentityVerificationStatus status, int page, int size);

    /**
     * One picture, still encrypted.
     */
    Optional<byte[]> image(UUID id, DocumentSide side);

    record VerificationSlice(List<IdentityVerification> items, boolean hasNext) {
    }
}
