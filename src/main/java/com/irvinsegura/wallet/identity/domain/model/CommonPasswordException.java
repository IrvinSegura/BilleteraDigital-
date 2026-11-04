package com.irvinsegura.wallet.identity.domain.model;

import com.irvinsegura.wallet.shared.domain.DomainException;

/**
 * The password is long enough but easy to guess: a common one, a pattern, or the user's own name or email.
 */
public class CommonPasswordException extends DomainException {

    public CommonPasswordException() {
        super("COMMON_PASSWORD", "Password is too common or easy to guess");
    }
}
