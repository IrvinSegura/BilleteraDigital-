package com.irvinsegura.wallet.identity;

import java.util.Optional;
import java.util.UUID;

/**
 * Public API of the identity module.
 */
public interface UserDirectory {

    /**
     * Finds a user by email, ignoring case and surrounding spaces. Malformed emails simply find nobody.
     */
    Optional<UserReference> findByEmail(String email);

    /**
     * Email and name of a user, for messages addressed to them. Events only carry user ids, so listeners look the
     * contact up when they need it.
     */
    Optional<UserContact> findContact(UUID userId);

    /**
     * Authorizes money leaving the user's wallets (withdrawals, transfers sent): takes a shared lock on the user until
     * the caller's transaction ends and checks the account is enabled and its email verified. Disabling the account
     * waits for operations in flight and rejects the ones after it, even with an access token issued earlier.
     *
     * @throws AccountDisabledException  if an administrator disabled the account
     * @throws EmailNotVerifiedException if the user has not confirmed their email address yet
     */
    void authorizeOutgoingMoney(UUID userId);

    /**
     * Authorizes a deposit the user makes: the same lock, but only the account status is checked, since unverified
     * accounts may add money.
     *
     * @throws AccountDisabledException if an administrator disabled the account
     */
    void authorizeDeposit(UUID userId);

    /**
     * Authorizes a change to where the user's money can go, such as adding a bank account: the same lock and status
     * check as a deposit, so a disabled account cannot prepare a destination with an access token issued earlier.
     *
     * @throws AccountDisabledException if an administrator disabled the account
     */
    void authorizeAccountChange(UUID userId);
}
