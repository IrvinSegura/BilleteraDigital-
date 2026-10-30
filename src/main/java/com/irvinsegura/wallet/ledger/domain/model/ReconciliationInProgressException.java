package com.irvinsegura.wallet.ledger.domain.model;

import com.irvinsegura.wallet.shared.domain.DomainException;

public class ReconciliationInProgressException extends DomainException {

    public ReconciliationInProgressException() {
        super("RECONCILIATION_IN_PROGRESS", "Another reconciliation is running; try again when it finishes");
    }
}
