package com.irvinsegura.wallet.identity.application;

import com.irvinsegura.wallet.identity.AccountDisabledException;
import com.irvinsegura.wallet.identity.EmailVerified;
import com.irvinsegura.wallet.identity.PasswordChanged;
import com.irvinsegura.wallet.identity.PasswordResetRequested;
import com.irvinsegura.wallet.identity.domain.model.AccountLockedException;
import com.irvinsegura.wallet.identity.domain.model.BreachedPasswordException;
import com.irvinsegura.wallet.identity.domain.model.PasswordUnchangedException;
import com.irvinsegura.wallet.identity.domain.model.Email;
import com.irvinsegura.wallet.identity.domain.model.IncorrectPasswordException;
import com.irvinsegura.wallet.identity.domain.model.InvalidEmailException;
import com.irvinsegura.wallet.identity.domain.model.InvalidOneTimeTokenException;
import com.irvinsegura.wallet.identity.domain.model.PasswordPolicy;
import com.irvinsegura.wallet.identity.domain.model.TokenPurpose;
import com.irvinsegura.wallet.identity.domain.model.User;
import com.irvinsegura.wallet.identity.domain.model.UserId;
import com.irvinsegura.wallet.identity.domain.model.UserNotFoundException;
import com.irvinsegura.wallet.identity.domain.port.BreachedPasswords;
import com.irvinsegura.wallet.identity.domain.port.PasswordHasher;
import com.irvinsegura.wallet.identity.domain.port.RefreshTokenRepository;
import com.irvinsegura.wallet.identity.domain.port.UserRepository;
import com.irvinsegura.wallet.shared.domain.Language;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final BreachedPasswords breachedPasswords;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final TransactionTemplate transaction;

    PasswordService(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
            PasswordHasher passwordHasher, PasswordVerifier passwordVerifier, SessionTokens sessionTokens,
            OneTimeTokenService tokens, BreachedPasswords breachedPasswords, ApplicationEventPublisher events,
            Clock clock, PlatformTransactionManager transactionManager) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordHasher = passwordHasher;
        this.passwordVerifier = passwordVerifier;
        this.sessionTokens = sessionTokens;
        this.tokens = tokens;
        this.breachedPasswords = breachedPasswords;
        this.events = events;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * Emails a reset link if the address belongs to an enabled account. Callers answer the same whatever happens
     * here, so the response never reveals who is registered. The language only applies to this email: whoever asks
     * may not be the owner, so it never changes the account's.
     *
     * @param language the language of the app asking; null to use the account's
     */
    @Transactional
    public void requestReset(String email, Language language) {
        findByEmail(email)
                .filter(User::isActive)
                .ifPresent(user -> events.publishEvent(
                        new PasswordResetRequested(user.id().value(), language, clock.instant())));
    }

    /**
     * Sets a new password from a reset link, lifts any lockout and ends every session. Following the link also
     * proves the user owns the address, so it verifies the email too.
     *
     * @throws InvalidOneTimeTokenException if the link is invalid, expired or was already used
     * @throws BreachedPasswordException     if the password appears in a known data breach
     */
    public void resetPassword(String rawToken, String newPassword) {
        // Checked first, so a weak password does not use up the link; the one built from the user's own name or
        // email is only known after, and its rollback gives the link back
        checkBeforeTransaction(newPassword);
        transaction.executeWithoutResult(status -> reset(rawToken, newPassword));
    }

    private void reset(String rawToken, String newPassword) {
        UserId userId = tokens.consume(rawToken, TokenPurpose.RESET_PASSWORD);
        User user = userRepository.findByIdForUpdate(userId).orElseThrow(InvalidOneTimeTokenException::new);
        PasswordPolicy.check(newPassword, user.email().value(), user.fullName());
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
     * @throws BreachedPasswordException  if the new password appears in a known data breach
     * @throws PasswordUnchangedException if the new password is the current one
     */
    public AuthTokens changePassword(UUID userId, String currentPassword, String newPassword) {
        checkBeforeTransaction(newPassword);
        AuthTokens tokens = transaction.execute(status -> change(userId, currentPassword, newPassword));
        // Thrown after the commit: the failed attempt must be stored even though the change fails
        if (tokens == null) {
            throw new IncorrectPasswordException();
        }
        return tokens;
    }

    // Null when the current password is wrong
    private AuthTokens change(UUID userId, String currentPassword, String newPassword) {
        User user = userRepository.findByIdForUpdate(new UserId(userId)).orElseThrow(UserNotFoundException::new);
        PasswordPolicy.check(newPassword, user.email().value(), user.fullName());
        Instant now = clock.instant();
        if (!user.isActive()) {
            throw new AccountDisabledException();
        }
        if (user.isLockedAt(now)) {
            throw new AccountLockedException(user.lockedUntil());
        }
        if (!passwordVerifier.verify(user, currentPassword, now)) {
            return null;
        }
        if (passwordHasher.matches(newPassword, user.passwordHash())) {
            throw new PasswordUnchangedException();
        }
        replacePassword(user, newPassword, CHANGE, now);
        return sessionTokens.startSession(user, now);
    }

    // The rules that need no user, then the breach lookup: a network call no database connection should wait on
    private void checkBeforeTransaction(String newPassword) {
        PasswordPolicy.check(newPassword);
        if (breachedPasswords.isBreached(newPassword)) {
            throw new BreachedPasswordException();
        }
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
