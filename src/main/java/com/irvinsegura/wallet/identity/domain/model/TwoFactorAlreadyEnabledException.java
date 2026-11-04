package com.irvinsegura.wallet.identity.domain.model;

import com.irvinsegura.wallet.shared.domain.DomainException;

public class TwoFactorAlreadyEnabledException extends DomainException {

    public TwoFactorAlreadyEnabledException() {
        super("TWO_FACTOR_ALREADY_ENABLED", "Two-factor authentication is already on; turn it off first to set up another app");
    }
}
