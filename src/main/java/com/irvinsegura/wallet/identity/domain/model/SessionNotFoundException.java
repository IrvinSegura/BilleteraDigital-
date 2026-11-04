package com.irvinsegura.wallet.identity.domain.model;

import com.irvinsegura.wallet.shared.domain.DomainException;

public class SessionNotFoundException extends DomainException {

    public SessionNotFoundException() {
        super("SESSION_NOT_FOUND", "Session not found");
    }
}
