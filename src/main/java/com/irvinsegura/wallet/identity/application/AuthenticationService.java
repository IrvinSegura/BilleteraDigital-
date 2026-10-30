package com.irvinsegura.wallet.identity.application;

import com.irvinsegura.wallet.identity.UserLockedOut;
import com.irvinsegura.wallet.identity.domain.model.AccessToken;
import com.irvinsegura.wallet.identity.domain.model.AccountLockedException;
import com.irvinsegura.wallet.identity.domain.model.Email;
import com.irvinsegura.wallet.identity.domain.model.InvalidCredentialsException;
import com.irvinsegura.wallet.identity.domain.model.InvalidEmailException;
import com.irvinsegura.wallet.identity.domain.model.InvalidRefreshTokenException;
import com.irvinsegura.wallet.identity.domain.model.LockoutPolicy;
import com.irvinsegura.wallet.identity.domain.model.RefreshToken;
import com.irvinsegura.wallet.identity.domain.model.RefreshTokenPolicy;
import com.irvinsegura.wallet.identity.domain.model.User;
import com.irvinsegura.wallet.identity.domain.port.AccessTokenIssuer;
import com.irvinsegura.wallet.identity.domain.port.PasswordHasher;
import com.irvinsegura.wallet.identity.domain.port.RefreshTokenRepository;
import com.irvinsegura.wallet.identity.domain.port.UserRepository;
import java.time.Clock;
import java.time.Duration;
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
    private final AccessTokenIssuer accessTokenIssuer;
    private final LockoutPolicy lockoutPolicy;
    private final RefreshTokenPolicy refreshTokenPolicy;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    // Checked against when the email is unknown, so both failure paths take the same time and leak nothing
    private final String unknownUserHash;

    AuthenticationService(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
            PasswordHasher passwordHasher, AccessTokenIssuer accessTokenIssuer, LockoutPolicy lockoutPolicy,
            RefreshTokenPolicy refreshTokenPolicy, ApplicationEventPublisher events, Clock clock) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordHasher = passwordHasher;
        this.accessTokenIssuer = accessTokenIssuer;
        this.lockoutPolicy = lockoutPolicy;
        this.refreshTokenPolicy = refreshTokenPolicy;
        this.events = events;
        this.clock = clock;
        this.unknownUserHash = passwordHasher.hash(UUID.randomUUID().toString());
    }

    // The failed-attempt counter must be stored even though the login fails
    @Transactional(noRollbackFor = InvalidCredentialsException.class)
    public AuthTokens login(String email, String password) {
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
        if (!passwordHasher.matches(password, user.passwordHash())) {
            user.recordFailedLogin(now, lockoutPolicy);
            userRepository.save(user);
            if (user.isLockedAt(now)) {
                log.warn("User {} locked until {} after {} failed logins", user.id(), user.lockedUntil(),
                        user.failedLoginAttempts());
                // Committed with the failed attempt thanks to noRollbackFor
                events.publishEvent(new UserLockedOut(user.id().value(), user.lockedUntil(), now));
            }
            throw new InvalidCredentialsException();
        }
        if (user.recordSuccessfulLogin()) {
            userRepository.save(user);
        }
        return issueTokens(user, UUID.randomUUID(), now);
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
        User user = userRepository.findById(current.userId()).orElseThrow(InvalidRefreshTokenException::new);
        current.revoke(now);
        refreshTokenRepository.save(current);
        return issueTokens(user, current.familyId(), now);
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

    private AuthTokens issueTokens(User user, UUID familyId, Instant now) {
        AccessToken accessToken = accessTokenIssuer.issue(user, now);
        RefreshToken.Issued refreshToken =
                RefreshToken.issue(user.id(), familyId, now, refreshTokenPolicy.timeToLive());
        refreshTokenRepository.save(refreshToken.token());
        return new AuthTokens(
                accessToken.value(),
                Duration.between(now, accessToken.expiresAt()),
                refreshToken.rawValue(),
                refreshTokenPolicy.timeToLive());
    }
}
