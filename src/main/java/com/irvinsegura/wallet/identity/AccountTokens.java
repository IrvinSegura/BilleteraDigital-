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

    /**
     * @return empty if the user is no longer active or the address belongs to an account by now, so nothing is sent
     *         to it
     */
    Optional<IssuedToken> issueEmailChange(UUID userId, String newEmail);

    /**
     * Gives a new staff account its first password and returns it, the only moment it exists unhashed.
     *
     * @return empty if the user is no longer active or already has a password, for example one they set from the
     *         sign-in page: an event delivered twice must not replace it
     */
    Optional<String> issueInitialPassword(UUID userId);
}
