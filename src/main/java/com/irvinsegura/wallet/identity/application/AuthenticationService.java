package com.irvinsegura.wallet.identity.application;

import com.irvinsegura.wallet.identity.AccountDisabledException;
import com.irvinsegura.wallet.identity.EmailVerificationRequested;
import com.irvinsegura.wallet.identity.TwoFactorRequiredException;
import com.irvinsegura.wallet.identity.UserSignedIn;
import com.irvinsegura.wallet.identity.domain.model.AccountLockedException;
import com.irvinsegura.wallet.identity.domain.model.Email;
import com.irvinsegura.wallet.identity.domain.model.EmailVerificationRequiredException;
import com.irvinsegura.wallet.identity.domain.model.InvalidCredentialsException;
import com.irvinsegura.wallet.identity.domain.model.InvalidEmailException;
import com.irvinsegura.wallet.identity.domain.model.InvalidRefreshTokenException;
import com.irvinsegura.wallet.identity.domain.model.RefreshToken;
import com.irvinsegura.wallet.identity.domain.model.User;
import com.irvinsegura.wallet.identity.domain.port.PasswordHasher;
import com.irvinsegura.wallet.identity.domain.port.RefreshTokenRepository;
import com.irvinsegura.wallet.identity.domain.port.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Login, refresh token rotation and logout.
 */
@Service
public class AuthenticationService {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordHasher passwordHasher;
    private final PasswordVerifier passwordVerifier;
    private final SecondFactorVerifier secondFactorVerifier;
    private final SessionTokens sessionTokens;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    // Checked against when the email is unknown, so both failure paths take the same time and leak nothing
    private final String unknownUserHash;

    AuthenticationService(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
            PasswordHasher passwordHasher, PasswordVerifier passwordVerifier,
            SecondFactorVerifier secondFactorVerifier, SessionTokens sessionTokens, ApplicationEventPublisher events,
            Clock clock) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordHasher = passwordHasher;
        this.passwordVerifier = passwordVerifier;
        this.secondFactorVerifier = secondFactorVerifier;
        this.sessionTokens = sessionTokens;
        this.events = events;
        this.clock = clock;
        this.unknownUserHash = passwordHasher.hash(UUID.randomUUID().toString());
    }

    /**
     * @param twoFactorCode      the authenticator app's code or a recovery code; needed only when two-factor
     *                           authentication is on
     * @param resendVerification for an account whose email is not verified yet: email a new link
     * @throws TwoFactorRequiredException         if it is on and no code was given
     * @throws EmailVerificationRequiredException if the account has not verified its email address
     */
    // The failed-attempt counter, and the request for a new link, must be stored even though the login fails
    @Transactional(noRollbackFor = {InvalidCredentialsException.class, EmailVerificationRequiredException.class})
    public AuthTokens login(String email, String password, String twoFactorCode, boolean resendVerification) {
        Instant now = clock.instant();
        Optional<User> found = findForLogin(email);
        if (found.isEmpty()) {
            passwordHasher.matches(password, unknownUserHash);
            throw new InvalidCredentialsException();
        }
        User user = found.get();
        if (user.isLockedAt(now)) {
            throw new AccountLockedException(user.lockedUntil());
        }
        // A wrong password is committed as a failed attempt (and maybe a lockout) thanks to noRollbackFor
        if (!passwordVerifier.verify(user, password, now)) {
            throw new InvalidCredentialsException();
        }
        // Only after the password matched, so the status of an account is not revealed to someone guessing
        if (!user.isActive()) {
            throw new AccountDisabledException();
        }
        // Whoever signed up with an address they don't own never gets a session. A new link only on request: each
        // one cancels the earlier links, which the user may be about to follow
        if (!user.isEmailVerified()) {
            if (resendVerification) {
                events.publishEvent(new EmailVerificationRequested(user.id().value(), now));
            }
            throw new EmailVerificationRequiredException();
        }
        // A wrong code counts like a wrong password, so the lockout bounds guessing it too
        if (!secondFactorVerifier.verify(user.id(), twoFactorCode, now)) {
            passwordVerifier.recordFailedAttempt(user, now);
            throw new InvalidCredentialsException();
        }
        if (user.recordSuccessfulLogin()) {
            userRepository.save(user);
        }
        AuthTokens tokens = sessionTokens.startSession(user, now);
        events.publishEvent(new UserSignedIn(user.id().value(), now));
        return tokens;
    }

    // The family revocation on token reuse must be stored even though the refresh fails
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public AuthTokens refresh(String rawRefreshToken) {
        Instant now = clock.instant();
        RefreshToken current = refreshTokenRepository.findByTokenHashForUpdate(RefreshToken.hash(rawRefreshToken))
                .orElseThrow(InvalidRefreshTokenException::new);
        if (current.isRevoked()) {
            // A rotated token used again means two parties hold the family; end it so neither can keep going
            refreshTokenRepository.revokeFamily(current.familyId(), now);
            log.warn("Refresh token reuse detected for user {}, family {} revoked", current.userId(),
                    current.familyId());
            throw new InvalidRefreshTokenException();
        }
        if (current.isExpiredAt(now)) {
            throw new InvalidRefreshTokenException();
        }
        // Disabling an account revokes its sessions; checking again covers a refresh racing with the disable
        User user = userRepository.findById(current.userId())
                .filter(User::isActive)
                .orElseThrow(InvalidRefreshTokenException::new);
        current.revoke(now);
        refreshTokenRepository.save(current);
        return sessionTokens.issue(user, current.familyId(), now);
    }

    /**
     * Ends the session the refresh token belongs to. Unknown or already revoked tokens are ignored, so logging out
     * twice is not an error.
     */
    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenRepository.findByTokenHashForUpdate(RefreshToken.hash(rawRefreshToken))
                .ifPresent(token -> refreshTokenRepository.revokeFamily(token.familyId(), clock.instant()));
    }

    private Optional<User> findForLogin(String email) {
        try {
            return userRepository.findByEmailForUpdate(Email.of(email));
        } catch (InvalidEmailException e) {
            return Optional.empty();
        }
    }
}
