package com.irvinsegura.wallet.identity.domain.model;

import com.irvinsegura.wallet.shared.domain.DomainException;

/**
 * The password was right, but the account cannot sign in until its owner follows the link emailed to the address.
 */
public class EmailVerificationRequiredException extends DomainException {

    public EmailVerificationRequiredException() {
        super("EMAIL_NOT_VERIFIED", "Confirm your email address before signing in");
    }
}
