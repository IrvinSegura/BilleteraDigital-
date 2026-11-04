package com.irvinsegura.wallet.accounts.domain.model;

import com.irvinsegura.wallet.shared.domain.DomainException;

public class MovementNotFoundException extends DomainException {

    public MovementNotFoundException() {
        super("MOVEMENT_NOT_FOUND", "Movement not found");
    }
}
