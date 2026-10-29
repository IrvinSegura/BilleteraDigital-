package com.irvinsegura.wallet.identity.domain.model;

import com.irvinsegura.wallet.shared.domain.DomainException;

public class InvalidEmailException extends DomainException {

    public InvalidEmailException() {
        super("INVALID_EMAIL", "Email address is not valid");
    }
}
