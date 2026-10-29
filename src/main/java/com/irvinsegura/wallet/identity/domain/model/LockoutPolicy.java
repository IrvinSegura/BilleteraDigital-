package com.irvinsegura.wallet.identity.domain.model;

import java.time.Duration;
import java.util.Objects;

/**
 * Locks an account for {@code lockDuration} after {@code maxFailedAttempts} consecutive failed logins.
 */
public record LockoutPolicy(int maxFailedAttempts, Duration lockDuration) {

    public LockoutPolicy {
        Objects.requireNonNull(lockDuration, "lockDuration must not be null");
        if (maxFailedAttempts < 1) {
            throw new IllegalArgumentException("maxFailedAttempts must be at least 1");
        }
        if (lockDuration.isNegative() || lockDuration.isZero()) {
            throw new IllegalArgumentException("lockDuration must be positive");
        }
    }
}
