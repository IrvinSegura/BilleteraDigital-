package com.irvinsegura.wallet.accounts.application;

import com.irvinsegura.wallet.accounts.domain.model.BankAccount;
import java.time.Instant;
import java.util.UUID;

/**
 * What the owner sees: enough to tell their accounts apart, never the full CLABE.
 */
public record BankAccountSummary(UUID id, String bankCode, String clabeLastFour, String holderName,
        Instant createdAt) {

    static BankAccountSummary from(BankAccount bankAccount) {
        return new BankAccountSummary(bankAccount.id(), bankAccount.clabe().bankCode(),
                bankAccount.clabe().lastFour(), bankAccount.holderName(), bankAccount.createdAt());
    }
}
