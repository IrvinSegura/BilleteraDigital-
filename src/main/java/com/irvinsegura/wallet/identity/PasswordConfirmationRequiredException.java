package com.irvinsegura.wallet.identity;

import com.irvinsegura.wallet.shared.domain.DomainException;

/**
 * Part of the public API: the operation needs the current password again, from an account without two-factor
 * authentication (accounts with it are asked for its code instead).
 */
public class PasswordConfirmationRequiredException extends DomainException {

    public PasswordConfirmationRequiredException() {
        super("PASSWORD_CONFIRMATION_REQUIRED", "Enter your password to confirm this operation");
    }
}
