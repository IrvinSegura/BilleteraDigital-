package com.irvinsegura.wallet.identity.application;

import com.irvinsegura.wallet.identity.AccountDisabledException;
import com.irvinsegura.wallet.identity.AccountStanding;
import com.irvinsegura.wallet.identity.EmailNotVerifiedException;
import com.irvinsegura.wallet.identity.IdentityNotVerifiedException;
import com.irvinsegura.wallet.identity.InvalidTwoFactorCodeException;
import com.irvinsegura.wallet.identity.PasswordConfirmationRequiredException;
import com.irvinsegura.wallet.identity.UserContact;
import com.irvinsegura.wallet.identity.UserDirectory;
import com.irvinsegura.wallet.identity.UserReference;
import com.irvinsegura.wallet.identity.domain.model.Role;
import com.irvinsegura.wallet.identity.domain.model.AccountLockedException;
import com.irvinsegura.wallet.identity.domain.model.Email;
import com.irvinsegura.wallet.identity.domain.model.IdentityVerification;
import com.irvinsegura.wallet.identity.domain.model.IdentityVerificationPolicy;
import com.irvinsegura.wallet.identity.domain.model.IdentityVerificationStatus;
import com.irvinsegura.wallet.identity.domain.model.IncorrectPasswordException;
import com.irvinsegura.wallet.identity.domain.model.InvalidEmailException;
import com.irvinsegura.wallet.identity.domain.model.User;
import com.irvinsegura.wallet.identity.domain.model.UserId;
import com.irvinsegura.wallet.identity.domain.port.IdentityVerificationRepository;
import com.irvinsegura.wallet.identity.domain.port.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class UserDirectoryService implements UserDirectory {

    private final UserRepository userRepository;
    private final SecondFactorVerifier secondFactorVerifier;
    private final PasswordVerifier passwordVerifier;
    private final IdentityVerificationRepository identityVerifications;
    private final IdentityVerificationPolicy identityPolicy;
    private final Clock clock;

    UserDirectoryService(UserRepository userRepository, SecondFactorVerifier secondFactorVerifier,
            PasswordVerifier passwordVerifier, IdentityVerificationRepository identityVerifications,
            IdentityVerificationPolicy identityPolicy, Clock clock) {
        this.userRepository = userRepository;
        this.secondFactorVerifier = secondFactorVerifier;
        this.passwordVerifier = passwordVerifier;
        this.identityVerifications = identityVerifications;
        this.identityPolicy = identityPolicy;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserReference> findByEmail(String email) {
        Email parsed;
        try {
            parsed = Email.of(email);
        } catch (InvalidEmailException e) {
            return Optional.empty();
        }
        return userRepository.findByEmail(parsed)
                .map(user -> new UserReference(user.id().value(), user.email().value()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserContact> findContact(UUID userId) {
        return userRepository.findById(new UserId(userId))
                .map(user -> new UserContact(user.id().value(), user.email().value(), user.fullName(),
                        user.language()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AccountStanding> findStanding(UUID userId) {
        Instant now = clock.instant();
        return userRepository.findById(new UserId(userId)).map(user -> {
            Optional<IdentityVerification> latest = identityVerifications.findLatestByUser(userId);
            String identity = identityVerifications.isVerified(userId) ? IdentityVerificationStatus.VERIFIED.name()
                    : latest.map(found -> found.status().name()).orElse("NONE");
            return new AccountStanding(userId, user.status().name(), user.isEmailVerified(),
                    user.isLockedAt(now) ? user.lockedUntil() : null, identity,
                    latest.map(IdentityVerification::submittedAt).orElse(null),
                    latest.filter(found -> found.status() == IdentityVerificationStatus.REJECTED)
                            .map(IdentityVerification::rejectionReason).orElse(null),
                    identityPolicy.requiredForWithdrawals(), user.createdAt());
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, String> findNames(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds.stream().map(UserId::new).toList()).stream()
                .collect(Collectors.toMap(user -> user.id().value(), User::fullName));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> activeSupportAgents() {
        return userRepository.findActiveIdsByRole(Role.SUPPORT);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findIdsMatching(String text, boolean byEmail, int limit) {
        String typed = text.strip().toLowerCase(Locale.ROOT);
        if (typed.isEmpty()) {
            return List.of();
        }
        return userRepository.findMatching(typed, limit).stream()
                .filter(user -> byEmail || user.fullName().toLowerCase(Locale.ROOT).contains(typed))
                .map(user -> user.id().value())
                .toList();
    }

    @Override
    @Transactional
    public void authorizeOutgoingMoney(UUID userId) {
        if (!lockForMoneyMovement(userId).isEmailVerified()) {
            throw new EmailNotVerifiedException();
        }
    }

    @Override
    @Transactional
    public void authorizeWithdrawal(UUID userId) {
        authorizeOutgoingMoney(userId);
        if (identityPolicy.requiredForWithdrawals() && !identityVerifications.isVerified(userId)) {
            throw new IdentityNotVerifiedException();
        }
    }

    @Override
    @Transactional
    public void authorizeDeposit(UUID userId) {
        lockForMoneyMovement(userId);
    }

    @Override
    @Transactional
    public void authorizeAccountChange(UUID userId) {
        lockForMoneyMovement(userId);
    }

    // A transaction of its own, committed even when the code is wrong, so the failed attempt is never rolled back
    // with the caller's change
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = InvalidTwoFactorCodeException.class)
    public void confirmSecondFactor(UUID userId, String code) {
        Instant now = clock.instant();
        User user = lockForCredentialCheck(userId, now);
        if (!secondFactorVerifier.verify(user.id(), code, now)) {
            passwordVerifier.recordFailedAttempt(user, now);
            throw new InvalidTwoFactorCodeException();
        }
    }

    // Same own transaction as confirmSecondFactor, for the same reason
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW,
            noRollbackFor = {InvalidTwoFactorCodeException.class, IncorrectPasswordException.class})
    public void confirmStepUp(UUID userId, String twoFactorCode, String currentPassword) {
        Instant now = clock.instant();
        User user = lockForCredentialCheck(userId, now);
        if (secondFactorVerifier.isEnabled(user.id())) {
            // The stronger factor when there is one: a password alone would let a phished password skip it
            if (!secondFactorVerifier.verify(user.id(), twoFactorCode, now)) {
                passwordVerifier.recordFailedAttempt(user, now);
                throw new InvalidTwoFactorCodeException();
            }
            return;
        }
        if (currentPassword == null || currentPassword.isBlank()) {
            throw new PasswordConfirmationRequiredException();
        }
        if (!passwordVerifier.verify(user, currentPassword, now)) {
            throw new IncorrectPasswordException();
        }
    }

    // Locks the user for the credential check, so concurrent wrong attempts are all counted
    /**
     * The password always, then the second factor when the user has one: for changes to the account itself, where a
     * session alone, or a code alone, is not enough. Same own transaction as {@link #confirmStepUp}.
     *
     * @throws IncorrectPasswordException    if the password is wrong
     * @throws InvalidTwoFactorCodeException if the user has a second factor and the code is wrong
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW,
            noRollbackFor = {InvalidTwoFactorCodeException.class, IncorrectPasswordException.class})
    public void confirmPasswordAndSecondFactor(UUID userId, String currentPassword, String twoFactorCode) {
        Instant now = clock.instant();
        User user = lockForCredentialCheck(userId, now);
        if (currentPassword == null || currentPassword.isBlank()) {
            throw new PasswordConfirmationRequiredException();
        }
        if (!passwordVerifier.verify(user, currentPassword, now)) {
            throw new IncorrectPasswordException();
        }
        if (!secondFactorVerifier.verify(user.id(), twoFactorCode, now)) {
            passwordVerifier.recordFailedAttempt(user, now);
            throw new InvalidTwoFactorCodeException();
        }
    }

    private User lockForCredentialCheck(UUID userId, Instant now) {
        User user = userRepository.findByIdForUpdate(new UserId(userId)).orElseThrow(AccountDisabledException::new);
        if (!user.isActive()) {
            throw new AccountDisabledException();
        }
        if (user.isLockedAt(now)) {
            throw new AccountLockedException(user.lockedUntil());
        }
        return user;
    }

    private User lockForMoneyMovement(UUID userId) {
        // Users are never deleted, so a missing one can only be a token for an account that never existed here
        User user = userRepository.findByIdForShare(new UserId(userId)).orElseThrow(AccountDisabledException::new);
        if (!user.isActive()) {
            throw new AccountDisabledException();
        }
        return user;
    }
}
