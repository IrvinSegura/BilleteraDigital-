package com.irvinsegura.wallet.transfers.domain.model;

public enum DepositStatus {
    // Waiting for the payer to complete the provider's checkout
    PENDING,
    // Credited to the wallet
    COMPLETED,
    // The payer did not complete the checkout in time
    EXPIRED,
    // The payment could not be started; nothing was charged
    FAILED,
    // Paid, but the wallet could no longer receive money (frozen, or the account disabled); the payment is refunded
    REJECTED
}
