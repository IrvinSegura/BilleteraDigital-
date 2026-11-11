package com.irvinsegura.wallet.identity.domain.model;

import java.time.Instant;

/**
 * Signed, short-lived token that authenticates API calls.
 */
public record AccessToken(String value, Instant issuedAt, Instant expiresAt) {

    // Written by the token issuer and read back when authenticating requests
    public static final String ROLES_CLAIM = "roles";
    // Whether the user has two-factor authentication on, so every login of theirs needed it; administrators must
    public static final String TWO_FACTOR_CLAIM = "two_factor";
    // Whether the account still has the password it was emailed, so all it can do is replace it
    public static final String PASSWORD_CHANGE_CLAIM = "password_change";
    // The session (refresh token family) the token was issued to, so the session list can tell which one is in use
    public static final String SESSION_CLAIM = "sid";
}
