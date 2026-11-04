package com.irvinsegura.wallet.identity.domain.model;

import com.irvinsegura.wallet.shared.domain.DomainException;

public class InvalidProfileException extends DomainException {

    public InvalidProfileException(String message) {
        super("INVALID_PROFILE", message);
    }
}
