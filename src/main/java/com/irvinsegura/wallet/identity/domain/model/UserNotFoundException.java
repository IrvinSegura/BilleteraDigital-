package com.irvinsegura.wallet.identity.domain.model;

import com.irvinsegura.wallet.shared.domain.DomainException;

public class UserNotFoundException extends DomainException {

    public UserNotFoundException() {
        super("USER_NOT_FOUND", "User not found");
    }
}
