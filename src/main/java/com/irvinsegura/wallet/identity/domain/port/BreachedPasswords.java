package com.irvinsegura.wallet.identity.domain.port;

/**
 * Passwords already published in data breaches. Implementations answer {@code false} when they cannot tell, so an
 * outage of the source never stops anyone from signing up or changing a password.
 */
public interface BreachedPasswords {

    boolean isBreached(String rawPassword);

    /** For when the check is turned off. */
    static BreachedPasswords none() {
        return rawPassword -> false;
    }
}
