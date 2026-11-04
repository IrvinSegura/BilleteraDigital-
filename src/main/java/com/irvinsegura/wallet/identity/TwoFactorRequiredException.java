package com.irvinsegura.wallet.identity;

import com.irvinsegura.wallet.shared.domain.DomainException;

/**
 * Part of the public API: the account has two-factor authentication on, and the operation needs its code.
 */
public class TwoFactorRequiredException extends DomainException {

    public TwoFactorRequiredException() {
        super("TWO_FACTOR_REQUIRED", "Enter the code from your authenticator app or a recovery code");
    }
}
