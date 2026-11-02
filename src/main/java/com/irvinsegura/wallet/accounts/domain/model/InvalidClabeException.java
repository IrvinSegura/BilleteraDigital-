package com.irvinsegura.wallet.accounts.domain.model;

import com.irvinsegura.wallet.shared.domain.DomainException;

public class InvalidClabeException extends DomainException {

    public InvalidClabeException(String message) {
        super("INVALID_CLABE", message);
    }
}
