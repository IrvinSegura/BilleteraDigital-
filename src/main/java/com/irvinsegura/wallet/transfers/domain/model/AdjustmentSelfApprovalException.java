package com.irvinsegura.wallet.transfers.domain.model;

import com.irvinsegura.wallet.shared.domain.DomainException;

public class AdjustmentSelfApprovalException extends DomainException {

    public AdjustmentSelfApprovalException() {
        super("ADJUSTMENT_SELF_APPROVAL", "A second administrator has to approve this adjustment");
    }
}
