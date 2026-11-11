package com.irvinsegura.wallet.support.domain.model;

import com.irvinsegura.wallet.shared.domain.DomainException;

/**
 * The operation isn't one of the customer being attended, so an agent can't escalate, nor learn about, anyone else's.
 */
public class OperationNotFoundException extends DomainException {

    public OperationNotFoundException() {
        super("SUPPORT_OPERATION_NOT_FOUND", "The operation is not one of this customer's");
    }
}
