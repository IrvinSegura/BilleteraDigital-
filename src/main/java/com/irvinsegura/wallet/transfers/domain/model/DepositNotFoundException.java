package com.irvinsegura.wallet.transfers.domain.model;

import com.irvinsegura.wallet.shared.domain.DomainException;

public class DepositNotFoundException extends DomainException {

    public DepositNotFoundException() {
        super("DEPOSIT_NOT_FOUND", "Deposit not found");
    }
}
