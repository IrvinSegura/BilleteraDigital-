package com.irvinsegura.wallet.accounts.domain.model;

public enum WalletStatus {
    ACTIVE,
    // Set by administrators (phase 4); a frozen wallet cannot move money
    FROZEN
}
