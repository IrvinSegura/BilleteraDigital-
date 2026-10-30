package com.irvinsegura.wallet.transfers.domain.model;

import com.irvinsegura.wallet.shared.domain.DomainException;

public class TransferNotFoundException extends DomainException {

    public TransferNotFoundException() {
        super("TRANSFER_NOT_FOUND", "Transfer not found");
    }
}
