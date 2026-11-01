package com.irvinsegura.wallet.identity.domain.model;

import java.time.Duration;
import java.util.Objects;

/**
 * How long each kind of email link works. Reset links are short-lived: whoever holds one can take over the account.
 */
public record OneTimeTokenPolicy(Duration emailVerificationTtl, Duration passwordResetTtl) {

    public OneTimeTokenPolicy {
        requirePositive(emailVerificationTtl, "emailVerificationTtl");
        requirePositive(passwordResetTtl, "passwordResetTtl");
    }

    public Duration timeToLive(TokenPurpose purpose) {
        return switch (purpose) {
            case VERIFY_EMAIL -> emailVerificationTtl;
            case RESET_PASSWORD -> passwordResetTtl;
        };
    }

    private static void requirePositive(Duration value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
