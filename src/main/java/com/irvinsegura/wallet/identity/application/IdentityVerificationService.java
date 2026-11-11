package com.irvinsegura.wallet.identity.application;

import com.irvinsegura.wallet.identity.AccountDisabledException;
import com.irvinsegura.wallet.identity.EmailNotVerifiedException;
import com.irvinsegura.wallet.identity.IdentityVerificationReviewed;
import com.irvinsegura.wallet.identity.IdentityVerificationSubmitted;
import com.irvinsegura.wallet.identity.domain.model.DocumentSide;
import com.irvinsegura.wallet.identity.domain.model.IdentityVerification;
import com.irvinsegura.wallet.identity.domain.model.IdentityVerificationConflictException;
import com.irvinsegura.wallet.identity.domain.model.IdentityVerificationLimitException;
import com.irvinsegura.wallet.identity.domain.model.IdentityVerificationNotFoundException;
import com.irvinsegura.wallet.identity.domain.model.IdentityVerificationPolicy;
import com.irvinsegura.wallet.identity.domain.model.IdentityVerificationStatus;
import com.irvinsegura.wallet.identity.domain.model.IneCredential;
import com.irvinsegura.wallet.identity.domain.model.User;
import com.irvinsegura.wallet.identity.domain.model.UserId;
import com.irvinsegura.wallet.identity.domain.model.UserNotFoundException;
import com.irvinsegura.wallet.identity.domain.port.DocumentImages;
import com.irvinsegura.wallet.identity.domain.port.IdentityVerificationRepository;
import com.irvinsegura.wallet.identity.domain.port.IdentityVerificationRepository.VerificationSlice;
import com.irvinsegura.wallet.identity.domain.port.SecretCipher;
import com.irvinsegura.wallet.identity.domain.port.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Verifying a customer's identity with their voter card (INE): the customer sends the card's data and three
 * pictures (both sides and their face), the data is checked at once, everything is stored encrypted, and an
 * administrator compares the pictures with the data before approving it. Admin endpoints are restricted to the ADMIN
 * role by the security configuration; the customer ones to USER.
 */
@Service
public class IdentityVerificationService {

    private static final Duration ATTEMPT_WINDOW = Duration.ofHours(24);
    private static final String FIELD_SEPARATOR = "\n";

    private final IdentityVerificationRepository verifications;
    private final UserRepository users;
    private final SecretCipher cipher;
    private final DocumentImages images;
    private final IdentityVerificationPolicy policy;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final TransactionTemplate transaction;

    IdentityVerificationService(IdentityVerificationRepository verifications, UserRepository users,
            SecretCipher cipher, DocumentImages images, IdentityVerificationPolicy policy,
            ApplicationEventPublisher events, Clock clock, PlatformTransactionManager transactionManager) {
        this.verifications = verifications;
        this.users = users;
        this.cipher = cipher;
        this.images = images;
        this.policy = policy;
        this.events = events;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly = true)
    public IdentityVerificationView status(UUID userId) {
        int attemptsLeft = attemptsLeft(userId, clock.instant());
        return verifications.findLatestByUser(userId)
                .map(verification -> IdentityVerificationView.of(verification, attemptsLeft))
                .orElseGet(() -> IdentityVerificationView.notStarted(attemptsLeft));
    }

    /**
     * Checks the card's data and pictures, and stores them encrypted to wait for review. The pictures are read and
     * encrypted before the user's row is locked, since that takes a moment.
     *
     * @throws com.irvinsegura.wallet.identity.domain.model.InvalidIdentityDocumentException if the data or a picture
     *         is not valid
     * @throws IdentityVerificationConflictException if one is pending or verified, or the card is another account's
     * @throws IdentityVerificationLimitException    after too many attempts in a day
     */
    public IdentityVerificationView submit(UUID userId, IdentityVerificationCommand command) {
        Instant now = clock.instant();
        IneCredential credential = IneCredential.of(command.firstNames(), command.firstSurname(),
                command.secondSurname(), command.curp(), command.voterKey(), command.cic(), command.citizenId(),
                command.expiryYear(), today(now));
        Map<DocumentSide, byte[]> encrypted = new EnumMap<>(DocumentSide.class);
        encrypted.put(DocumentSide.FRONT, cipher.encrypt(images.sanitize(command.front())));
        encrypted.put(DocumentSide.BACK, cipher.encrypt(images.sanitize(command.back())));
        encrypted.put(DocumentSide.SELFIE, cipher.encrypt(images.sanitize(command.selfie())));
        String data = cipher.encrypt(serialize(credential));
        // Labeled, so a CURP and a voter key never share a fingerprint
        String curpFingerprint = cipher.fingerprint("CURP:" + credential.curp());
        String voterKeyFingerprint = cipher.fingerprint("VOTER_KEY:" + credential.voterKey());
        try {
            return transaction.execute(status -> {
                User user = users.findByIdForUpdate(new UserId(userId)).orElseThrow(UserNotFoundException::new);
                if (!user.isActive()) {
                    throw new AccountDisabledException();
                }
                if (!user.isEmailVerified()) {
                    throw new EmailNotVerifiedException();
                }
                verifications.findLatestByUser(userId).filter(IdentityVerification::isOpen).ifPresent(open -> {
                    throw new IdentityVerificationConflictException(
                            open.status() == IdentityVerificationStatus.VERIFIED
                                    ? IdentityVerificationConflictException.VERIFIED
                                    : IdentityVerificationConflictException.PENDING);
                });
                int attemptsLeft = attemptsLeft(userId, now);
                if (attemptsLeft <= 0) {
                    throw new IdentityVerificationLimitException();
                }
                if (verifications.documentInUse(curpFingerprint, voterKeyFingerprint, userId)) {
                    throw new IdentityVerificationConflictException(
                            IdentityVerificationConflictException.DOCUMENT_IN_USE);
                }
                IdentityVerification verification = IdentityVerification.submit(userId, credential, data,
                        curpFingerprint, voterKeyFingerprint, user.fullName(), now);
                verifications.insert(verification, encrypted);
                events.publishEvent(new IdentityVerificationSubmitted(userId, verification.id(), now));
                return IdentityVerificationView.of(verification, attemptsLeft - 1);
            });
        } catch (DataIntegrityViolationException e) {
            // Another account sent the same card at the same moment; the database's unique index kept it out
            throw new IdentityVerificationConflictException(IdentityVerificationConflictException.DOCUMENT_IN_USE);
        }
    }

    /**
     * Whether the user's identity is verified, for the operations that require it.
     */
    @Transactional(readOnly = true)
    public boolean isVerified(UUID userId) {
        return verifications.isVerified(userId);
    }

    /**
     * Whether a pending or verified identity fixes the user's name, which must match the card.
     */
    @Transactional(readOnly = true)
    public boolean nameLocked(UUID userId) {
        return verifications.findLatestByUser(userId).filter(IdentityVerification::isOpen).isPresent();
    }

    /**
     * @param status {@code null} for all
     */
    @Transactional(readOnly = true)
    public IdentityVerificationPage list(IdentityVerificationStatus status, int page, int size) {
        VerificationSlice slice = verifications.page(status, page, size);
        Map<UUID, User> owners = users.findAllById(slice.items().stream().map(v -> new UserId(v.userId())).toList())
                .stream().collect(Collectors.toMap(user -> user.id().value(), Function.identity()));
        List<IdentityVerificationSummary> items = slice.items().stream()
                .map(v -> IdentityVerificationSummary.of(v, owners.get(v.userId())))
                .toList();
        return new IdentityVerificationPage(items, page, size, slice.hasNext(), verifications.countPending());
    }

    /**
     * Everything the reviewer compares with the pictures, decrypted.
     */
    @Transactional(readOnly = true)
    public IdentityVerificationDetail detail(UUID id) {
        return detail(verifications.findById(id).orElseThrow(IdentityVerificationNotFoundException::new));
    }

    /**
     * One picture, decrypted, for the reviewer.
     */
    @Transactional(readOnly = true)
    public byte[] image(UUID id, DocumentSide side) {
        return verifications.image(id, side).map(cipher::decrypt)
                .orElseThrow(IdentityVerificationNotFoundException::new);
    }

    /**
     * @throws IdentityVerificationConflictException {@code ALREADY_REVIEWED} if someone reviewed it first
     */
    @Transactional
    public IdentityVerificationDetail approve(UUID id, UUID adminId) {
        IdentityVerification verification = lock(id);
        Instant now = clock.instant();
        verification.approve(adminId, now);
        verifications.updateReview(verification);
        events.publishEvent(new IdentityVerificationReviewed(verification.userId(), id,
                verification.status().name(), adminId, null, now));
        return detail(verification);
    }

    /**
     * @param reason shown to the customer, so they know what to fix before sending it again
     * @throws IdentityVerificationConflictException {@code ALREADY_REVIEWED} if someone reviewed it first
     */
    @Transactional
    public IdentityVerificationDetail reject(UUID id, UUID adminId, String reason) {
        IdentityVerification verification = lock(id);
        Instant now = clock.instant();
        verification.reject(adminId, reason, now);
        verifications.updateReview(verification);
        events.publishEvent(new IdentityVerificationReviewed(verification.userId(), id,
                verification.status().name(), adminId, verification.rejectionReason(), now));
        return detail(verification);
    }

    private IdentityVerification lock(UUID id) {
        return verifications.findByIdForUpdate(id).orElseThrow(IdentityVerificationNotFoundException::new);
    }

    private IdentityVerificationDetail detail(IdentityVerification verification) {
        User owner = users.findById(new UserId(verification.userId())).orElseThrow(UserNotFoundException::new);
        return IdentityVerificationDetail.of(verification, owner, deserialize(cipher.decrypt(verification.encryptedData())));
    }

    private int attemptsLeft(UUID userId, Instant now) {
        return Math.max(0, policy.maxAttemptsPerDay()
                - verifications.countSubmittedSince(userId, now.minus(ATTEMPT_WINDOW)));
    }

    private static LocalDate today(Instant now) {
        return now.atOffset(ZoneOffset.UTC).toLocalDate();
    }

    // Names only have letters, spaces, dots, apostrophes and dashes, so a line break can't be part of a field
    private static String serialize(IneCredential credential) {
        return String.join(FIELD_SEPARATOR, credential.firstNames(), credential.firstSurname(),
                credential.secondSurname() == null ? "" : credential.secondSurname(), credential.curp(),
                credential.voterKey(), credential.cic(), credential.citizenId(),
                String.valueOf(credential.expiryYear()));
    }

    private static IneCredential deserialize(String data) {
        String[] fields = data.split(FIELD_SEPARATOR, -1);
        return new IneCredential(fields[0], fields[1], fields[2].isEmpty() ? null : fields[2], fields[3], fields[4],
                fields[5], fields[6], Integer.parseInt(fields[7]));
    }
}
