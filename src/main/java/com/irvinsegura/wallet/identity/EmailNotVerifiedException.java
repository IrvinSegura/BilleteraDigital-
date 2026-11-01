package com.irvinsegura.wallet.identity;

import com.irvinsegura.wallet.shared.domain.DomainException;

/**
 * Part of the public API: money cannot leave an account until its owner proves they own the email address, so an
 * account registered with someone else's address cannot take money out.
 */
public class EmailNotVerifiedException extends DomainException {

    public EmailNotVerifiedException() {
        super("EMAIL_NOT_VERIFIED", "Confirm your email address before sending or withdrawing money");
    }
}
