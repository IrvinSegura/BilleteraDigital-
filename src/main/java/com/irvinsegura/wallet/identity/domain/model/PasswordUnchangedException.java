package com.irvinsegura.wallet.identity.domain.model;

import com.irvinsegura.wallet.shared.domain.DomainException;

/**
 * The new password is the current one, which would also get around having to replace a generated password.
 */
public class PasswordUnchangedException extends DomainException {

    public PasswordUnchangedException() {
        super("PASSWORD_UNCHANGED", "The new password must be different from the current one");
    }
}
