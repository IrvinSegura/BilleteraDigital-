package com.irvinsegura.wallet.identity.domain.port;

public interface PasswordHasher {

    /**
     * Stored instead of a hash while an account has no password yet. No password matches it.
     */
    String NO_PASSWORD = "{none}";

    String hash(String rawPassword);

    boolean matches(String rawPassword, String passwordHash);
}
