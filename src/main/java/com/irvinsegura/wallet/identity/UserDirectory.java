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

    /**
     * Asks a user with two-factor authentication on for a code before a sensitive change, such as adding a bank
     * account. Does nothing for users without it. Runs and commits on its own, so a wrong code counts toward the
     * lockout even though the change is refused. Call it before the caller's transaction starts, for the same reason
     * as {@link #confirmStepUp}.
     *
     * @param code the authenticator app's code or a recovery code
     * @throws TwoFactorRequiredException    if the user has it on and gave no code
     * @throws InvalidTwoFactorCodeException if the code is wrong
     */
    void confirmSecondFactor(UUID userId, String code);

    /**
     * Step-up check before money leaves: proof that whoever holds the session is the account owner, so a stolen
     * access token alone cannot empty the wallet. Users with two-factor authentication on give its code; the others
     * give their current password. Same rules as {@link #confirmSecondFactor}: it commits on its own and a wrong
     * credential counts toward the lockout. Call it outside any transaction: it locks the user row in its own, which
     * inside another would hold a second connection and wait on any lock the caller already took.
     *
     * @param twoFactorCode   the authenticator app's code or a recovery code, used when two-factor is on
     * @param currentPassword the account's password, used when two-factor is off
     * @throws TwoFactorRequiredException            if two-factor is on and no code was given
     * @throws InvalidTwoFactorCodeException         if the code is wrong
     * @throws PasswordConfirmationRequiredException if two-factor is off and no password was given; a wrong one
     *                                               answers {@code INCORRECT_PASSWORD}
     */
    void confirmStepUp(UUID userId, String twoFactorCode, String currentPassword);
}
