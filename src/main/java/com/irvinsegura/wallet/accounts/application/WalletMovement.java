package com.irvinsegura.wallet.accounts.application;

import com.irvinsegura.wallet.ledger.AccountMovement;

/**
 * A movement of a wallet with what its operation adds; both are {@code null} for operations without another party,
 * such as deposits and withdrawals.
 */
public record WalletMovement(AccountMovement movement, String counterpartyName, String description) {
}
