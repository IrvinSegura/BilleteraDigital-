package com.irvinsegura.wallet.identity.application;

import com.irvinsegura.wallet.identity.AccountDisabledException;
import com.irvinsegura.wallet.identity.EmailNotVerifiedException;
import com.irvinsegura.wallet.identity.InvalidTwoFactorCodeException;
import com.irvinsegura.wallet.identity.PasswordConfirmationRequiredException;
import com.irvinsegura.wallet.identity.UserContact;
import com.irvinsegura.wallet.identity.UserDirectory;
import com.irvinsegura.wallet.identity.UserReference;
import com.irvinsegura.wallet.identity.domain.model.AccountLockedException;
import com.irvinsegura.wallet.identity.domain.model.Email;
import com.irvinsegura.wallet.identity.domain.model.IncorrectPasswordException;
import com.irvinsegura.wallet.identity.domain.model.InvalidEmailException;
import com.irvinsegura.wallet.identity.domain.model.User;
import com.irvinsegura.wallet.identity.domain.model.UserId;
import com.irvinsegura.wallet.identity.domain.port.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class UserDirectoryService implements UserDirectory {

    private final UserRepository userRepository;
    private final SecondFactorVerifier secondFactorVerifier;
    private final PasswordVerifier passwordVerifier;
    private final Clock clock;

    UserDirectoryService(UserRepository userRepository, SecondFactorVerifier secondFactorVerifier,
            PasswordVerifier passwordVerifier, Clock clock) {
        this.userRepository = userRepository;
        this.secondFactorVerifier = secondFactorVerifier;
        this.passwordVerifier = passwordVerifier;
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
                .map(user -> new UserContact(user.id().value(), user.email().value(), user.fullName()));
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
