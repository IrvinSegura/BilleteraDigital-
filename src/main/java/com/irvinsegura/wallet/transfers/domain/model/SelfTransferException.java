package com.irvinsegura.wallet.transfers.domain.model;

import com.irvinsegura.wallet.shared.domain.DomainException;

public class SelfTransferException extends DomainException {

    public SelfTransferException() {
        super("SELF_TRANSFER", "You cannot transfer money to yourself");
    }
}
