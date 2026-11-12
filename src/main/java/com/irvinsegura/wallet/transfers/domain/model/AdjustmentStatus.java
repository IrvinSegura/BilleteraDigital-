package com.irvinsegura.wallet.transfers.domain.model;

public enum AdjustmentStatus {
    // Above the single-approval limit: waits for a second administrator
    PENDING_APPROVAL,
    APPLIED,
    REJECTED
}
