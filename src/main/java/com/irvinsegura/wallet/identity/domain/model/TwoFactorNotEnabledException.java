package com.irvinsegura.wallet.identity.domain.model;

import com.irvinsegura.wallet.shared.domain.DomainException;

public class TwoFactorNotEnabledException extends DomainException {

    public TwoFactorNotEnabledException() {
        super("TWO_FACTOR_NOT_ENABLED", "Two-factor authentication is not set up for this account");
    }
}
