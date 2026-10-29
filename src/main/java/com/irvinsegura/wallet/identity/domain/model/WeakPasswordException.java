package com.irvinsegura.wallet.identity.domain.model;

import com.irvinsegura.wallet.shared.domain.DomainException;

public class WeakPasswordException extends DomainException {

    public WeakPasswordException(String message) {
        super("WEAK_PASSWORD", message);
    }
}
