package com.irvinsegura.wallet.identity.application;

import com.irvinsegura.wallet.identity.UserLockedOut;
import com.irvinsegura.wallet.identity.domain.model.LockoutPolicy;
import com.irvinsegura.wallet.identity.domain.model.User;
import com.irvinsegura.wallet.identity.domain.port.PasswordHasher;
import com.irvinsegura.wallet.identity.domain.port.UserRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Checks a user's password and applies the lockout policy, in the caller's transaction. Login and changing the
 * password share it, so a stolen access token cannot be used to guess the password without locking the account.
 */
@Component
class PasswordVerifier {

    private static final Logger log = LoggerFactory.getLogger(PasswordVerifier.class);

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final LockoutPolicy lockoutPolicy;
    private final ApplicationEventPublisher events;

    PasswordVerifier(UserRepository userRepository, PasswordHasher passwordHasher, LockoutPolicy lockoutPolicy,
            ApplicationEventPublisher events) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.lockoutPolicy = lockoutPolicy;
        this.events = events;
    }

    /**
     * Records a failed attempt when the password is wrong, so the caller's transaction must commit even though the
     * request then fails (see {@code noRollbackFor} on the callers).
     */
    boolean verify(User user, String rawPassword, Instant now) {
        if (passwordHasher.matches(rawPassword, user.passwordHash())) {
            return true;
        }
        recordFailedAttempt(user, now);
        return false;
    }

    /**
     * Counts a wrong credential of any kind, e.g. a wrong second factor, toward the lockout. Same transaction rule as
     * {@link #verify}.
     */
    void recordFailedAttempt(User user, Instant now) {
        user.recordFailedLogin(now, lockoutPolicy);
        userRepository.save(user);
        if (user.isLockedAt(now)) {
            log.warn("User {} locked until {} after {} failed attempts", user.id(), user.lockedUntil(),
                    user.failedLoginAttempts());
            events.publishEvent(new UserLockedOut(user.id().value(), user.lockedUntil(), now));
        }
    }
}
