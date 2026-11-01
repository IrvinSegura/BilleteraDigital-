package com.irvinsegura.wallet.payments.domain.model;

import com.irvinsegura.wallet.shared.domain.DomainException;

/**
 * The provider reports a different amount than the one registered. It can only be a bug or tampering, so the
 * payment is never credited automatically.
 */
public class PaymentAmountMismatchException extends DomainException {

    public PaymentAmountMismatchException(String message) {
        super("PAYMENT_AMOUNT_MISMATCH", message);
    }
}
