package com.irvinsegura.wallet.transfers.domain.model;

import com.irvinsegura.wallet.shared.domain.DomainException;

public class AdjustmentNotFoundException extends DomainException {

    public AdjustmentNotFoundException() {
        super("ADJUSTMENT_NOT_FOUND", "Adjustment not found");
    }
}
