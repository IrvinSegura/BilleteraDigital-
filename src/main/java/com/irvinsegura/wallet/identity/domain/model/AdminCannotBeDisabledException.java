package com.irvinsegura.wallet.identity.domain.model;

import com.irvinsegura.wallet.shared.domain.DomainException;

public class AdminCannotBeDisabledException extends DomainException {

    public AdminCannotBeDisabledException() {
        super("ADMIN_CANNOT_BE_DISABLED", "The administrator account cannot be disabled");
    }
}
