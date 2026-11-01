package com.irvinsegura.wallet.identity;

import java.time.Instant;

/**
 * Secret for an email link. Treat it like a password: put it in the email and nowhere else (no logs, no events).
 */
public record IssuedToken(String value, Instant expiresAt) {

    @Override
    public String toString() {
        return "IssuedToken[value=***, expiresAt=%s]".formatted(expiresAt);
    }
}
