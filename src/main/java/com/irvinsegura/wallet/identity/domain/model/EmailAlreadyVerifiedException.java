package com.irvinsegura.wallet.identity.domain.model;

import com.irvinsegura.wallet.shared.domain.DomainException;

public class EmailAlreadyVerifiedException extends DomainException {

    public EmailAlreadyVerifiedException() {
        super("EMAIL_ALREADY_VERIFIED", "Your email address is already verified");
    }
}
