package com.irvinsegura.wallet.identity.domain.model;

import java.time.Instant;

/**
 * Signed, short-lived token that authenticates API calls.
 */
public record AccessToken(String value, Instant issuedAt, Instant expiresAt) {

    // Written by the token issuer and read back when authenticating requests
    public static final String ROLES_CLAIM = "roles";
}
