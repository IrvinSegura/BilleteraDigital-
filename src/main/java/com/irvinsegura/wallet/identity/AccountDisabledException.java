package com.irvinsegura.wallet.identity;

import com.irvinsegura.wallet.shared.domain.DomainException;

/**
 * Part of the public API: operations that move money reject a disabled account with it.
 */
public class AccountDisabledException extends DomainException {

    public AccountDisabledException() {
        super("ACCOUNT_DISABLED", "This account is disabled; contact support");
    }
}
