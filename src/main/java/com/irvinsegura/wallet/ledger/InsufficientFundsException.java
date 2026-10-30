package com.irvinsegura.wallet.ledger;

import com.irvinsegura.wallet.shared.domain.DomainException;

public class InsufficientFundsException extends DomainException {

    public InsufficientFundsException() {
        super("INSUFFICIENT_FUNDS", "Not enough balance");
    }
}
