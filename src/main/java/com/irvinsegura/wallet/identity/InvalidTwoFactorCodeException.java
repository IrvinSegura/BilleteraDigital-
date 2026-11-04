package com.irvinsegura.wallet.identity;

import com.irvinsegura.wallet.shared.domain.DomainException;

/**
 * Part of the public API: the second factor given for an operation was wrong; it counts toward the lockout.
 */
public class InvalidTwoFactorCodeException extends DomainException {

    public InvalidTwoFactorCodeException() {
        super("INVALID_TWO_FACTOR_CODE", "The code is wrong, expired or already used");
    }
}
