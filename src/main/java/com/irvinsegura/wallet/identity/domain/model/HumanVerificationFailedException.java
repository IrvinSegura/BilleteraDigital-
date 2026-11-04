package com.irvinsegura.wallet.identity.domain.model;

import com.irvinsegura.wallet.shared.domain.DomainException;

/**
 * The CAPTCHA token is missing, expired, already used or was refused by the provider.
 */
public class HumanVerificationFailedException extends DomainException {

    public HumanVerificationFailedException() {
        super("CAPTCHA_FAILED", "Complete the human check and try again");
    }
}
