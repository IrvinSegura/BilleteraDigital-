package com.irvinsegura.wallet.transfers.domain.model;

import com.irvinsegura.wallet.shared.domain.DomainException;

public class AdjustmentNotPendingException extends DomainException {

    public AdjustmentNotPendingException() {
        super("ADJUSTMENT_NOT_PENDING", "The adjustment was already applied or rejected");
    }
}
