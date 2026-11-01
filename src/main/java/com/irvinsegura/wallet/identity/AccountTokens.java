package com.irvinsegura.wallet.identity;

import java.util.Optional;
import java.util.UUID;

/**
 * Public API of the identity module for whoever sends account emails. Each call replaces the user's earlier links for
 * the same purpose, so only the newest one works.
 * <p>
 * Call it inside the transaction that sends the email: if sending fails, the token is rolled back with it and the
 * retried send issues a fresh one, so no link is ever stored that was not delivered.
 */
public interface AccountTokens {

    /**
     * @return empty if the user does not exist or is already verified, so a stale request sends nothing
     */
    Optional<IssuedToken> issueEmailVerification(UUID userId);

    /**
     * @return empty if the user does not exist or is disabled
     */
    Optional<IssuedToken> issuePasswordReset(UUID userId);
}
