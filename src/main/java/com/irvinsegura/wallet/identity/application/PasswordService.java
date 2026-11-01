package com.irvinsegura.wallet.identity.application;

import com.irvinsegura.wallet.identity.AccountDisabledException;
import com.irvinsegura.wallet.identity.EmailVerified;
import com.irvinsegura.wallet.identity.PasswordChanged;
import com.irvinsegura.wallet.identity.PasswordResetRequested;
import com.irvinsegura.wallet.identity.domain.model.AccountLockedException;
import com.irvinsegura.wallet.identity.domain.model.Email;
import com.irvinsegura.wallet.identity.domain.model.IncorrectPasswordException;
import com.irvinsegura.wallet.identity.domain.model.InvalidEmailException;
import com.irvinsegura.wallet.identity.domain.model.InvalidOneTimeTokenException;
import com.irvinsegura.wallet.identity.domain.model.PasswordPolicy;
import com.irvinsegura.wallet.identity.domain.model.TokenPurpose;
import com.irvinsegura.wallet.identity.domain.model.User;
import com.irvinsegura.wallet.identity.domain.model.UserId;
import com.irvinsegura.wallet.identity.domain.model.UserNotFoundException;
import com.irvinsegura.wallet.identity.domain.port.PasswordHasher;
import com.irvinsegura.wallet.identity.domain.port.RefreshTokenRepository;
import com.irvinsegura.wallet.identity.domain.port.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Forgotten passwords (reset through an email link) and password changes from inside the account. Both end every
 * other session, because whoever knew the old password may still hold one.
 */
@Service
public class PasswordService {

    static final String CHANGE = "CHANGE";
    static final String RESET = "RESET";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordHasher passwordHasher;
    private final PasswordVerifier passwordVerifier;
    private final SessionTokens sessionTokens;
    private final OneTimeTokenService tokens;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    PasswordService(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
            PasswordHasher passwordHasher, PasswordVerifier passwordVerifier, SessionTokens sessionTokens,
            OneTimeTokenService tokens, ApplicationEventPublisher events, Clock clock) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordHasher = passwordHasher;
        this.passwordVerifier = passwordVerifier;
        this.sessionTokens = sessionTokens;
        this.tokens = tokens;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Emails a reset link if the address belongs to an enabled account. Callers answer the same whatever happens
     * here, so the response never reveals who is registered.
     */
    @Transactional
    public void requestReset(String email) {
        findByEmail(email)
                .filter(User::isActive)
                .ifPresent(user -> events.publishEvent(
                        new PasswordResetRequested(user.id().value(), clock.instant())));
    }

    /**
     * Sets a new password from a reset link, lifts any lockout and ends every session. Following the link also
     * proves the user owns the address, so it verifies the email too.
     *
     * @throws InvalidOneTimeTokenException if the link is invalid, expired or was already used
     */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        // Checked first, so a weak password does not use up the link
        PasswordPolicy.check(newPassword);
        UserId userId = tokens.consume(rawToken, TokenPurpose.RESET_PASSWORD);
        User user = userRepository.findByIdForUpdate(userId).orElseThrow(InvalidOneTimeTokenException::new);
        Instant now = clock.instant();
        boolean newlyVerified = user.verifyEmail(now);
        replacePassword(user, newPassword, RESET, now);
        if (newlyVerified) {
            events.publishEvent(new EmailVerified(userId.value(), now));
        }
    }

    /**
     * Changes the password of a signed-in user and ends every session, then starts a new one for the caller, so only
     * the device that changed it stays signed in.
     *
     * @throws IncorrectPasswordException if the current password is wrong; it counts as a failed login
     */
    // The failed attempt must be stored even though the change fails
    @Transactional(noRollbackFor = IncorrectPasswordException.class)
    public AuthTokens changePassword(UUID userId, String currentPassword, String newPassword) {
        PasswordPolicy.check(newPassword);
        User user = userRepository.findByIdForUpdate(new UserId(userId)).orElseThrow(UserNotFoundException::new);
        Instant now = clock.instant();
        if (!user.isActive()) {
            throw new AccountDisabledException();
        }
        if (user.isLockedAt(now)) {
            throw new AccountLockedException(user.lockedUntil());
        }
        if (!passwordVerifier.verify(user, currentPassword, now)) {
            throw new IncorrectPasswordException();
        }
        replacePassword(user, newPassword, CHANGE, now);
        return sessionTokens.startSession(user, now);
    }

    private void replacePassword(User user, String newPassword, String method, Instant now) {
        user.changePassword(passwordHasher.hash(newPassword));
        userRepository.save(user);
        refreshTokenRepository.revokeAllForUser(user.id(), now);
        events.publishEvent(new PasswordChanged(user.id().value(), method, now));
    }

    private Optional<User> findByEmail(String email) {
        try {
            return userRepository.findByEmail(Email.of(email));
        } catch (InvalidEmailException e) {
            return Optional.empty();
        }
    }
}
