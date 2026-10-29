package com.irvinsegura.wallet.identity.domain.model;

import java.nio.charset.StandardCharsets;

/**
 * Length-based password rules, following NIST SP 800-63B: no composition rules, long passphrases allowed.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 64;
    // BCrypt ignores everything after 72 bytes, so longer passwords would give a false sense of strength
    private static final int MAX_BYTES = 72;

    private PasswordPolicy() {
    }

    public static void check(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new WeakPasswordException("Password must not be blank");
        }
        int length = rawPassword.codePointCount(0, rawPassword.length());
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            throw new WeakPasswordException(
                    "Password must have between %d and %d characters".formatted(MIN_LENGTH, MAX_LENGTH));
        }
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new WeakPasswordException("Password must not exceed %d bytes".formatted(MAX_BYTES));
        }
    }
}
