package com.irvinsegura.wallet.identity.domain.model;

import com.irvinsegura.wallet.shared.domain.DomainException;

public class AdminTwoFactorRequiredException extends DomainException {

    public AdminTwoFactorRequiredException() {
        super("ADMIN_TWO_FACTOR_REQUIRED",
                "Administrators must turn on two-factor authentication and sign in again with it");
    }
}
