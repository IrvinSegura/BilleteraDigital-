package com.irvinsegura.wallet.payments.domain.model;

public enum PaymentStatus {
    // Registered with the operation it pays for, not sent to the provider yet
    CREATED,
    // Checkout open at the provider, waiting for the payer
    PENDING,
    SUCCEEDED,
    // Could not be started, or was never started
    FAILED,
    // The payer did not complete the checkout in time
    EXPIRED,
    // Refund requested; the provider is called by a listener and retried until it answers
    REFUND_PENDING,
    REFUNDED
}
