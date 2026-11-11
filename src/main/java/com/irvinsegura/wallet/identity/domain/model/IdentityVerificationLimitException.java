package com.irvinsegura.wallet.identity.domain.model;

import com.irvinsegura.wallet.shared.domain.DomainException;

/**
 * Too many verifications sent lately: someone trying card after card has to wait.
 */
public class IdentityVerificationLimitException extends DomainException {

    public IdentityVerificationLimitException() {
        super("IDENTITY_VERIFICATION_LIMIT", "Too many identity verifications sent; try again tomorrow");
    }
}
