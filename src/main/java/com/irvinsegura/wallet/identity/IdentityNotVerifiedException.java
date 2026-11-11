package com.irvinsegura.wallet.identity;

import com.irvinsegura.wallet.shared.domain.DomainException;

/**
 * Part of the public API: money can't leave for a bank account until the owner's identity is verified with an
 * official document, so a stolen or made-up account can't cash out.
 */
public class IdentityNotVerifiedException extends DomainException {

    public IdentityNotVerifiedException() {
        super("IDENTITY_NOT_VERIFIED", "Verify your identity before withdrawing money");
    }
}
