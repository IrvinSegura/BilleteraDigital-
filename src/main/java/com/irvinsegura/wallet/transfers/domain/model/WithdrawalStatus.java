package com.irvinsegura.wallet.transfers.domain.model;

public enum WithdrawalStatus {
    // Taken from the wallet and held in transit; the bank has not confirmed the payout yet
    PENDING,
    // The bank confirmed the payout
    PAID,
    // The bank refused the payout; the money went back to the wallet
    FAILED
}
