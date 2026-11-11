package com.irvinsegura.wallet.identity.domain.model;

import com.irvinsegura.wallet.shared.domain.DomainException;

public class IdentityVerificationNotFoundException extends DomainException {

    public IdentityVerificationNotFoundException() {
        super("IDENTITY_VERIFICATION_NOT_FOUND", "Identity verification not found");
    }
}
