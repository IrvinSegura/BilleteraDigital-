package com.irvinsegura.wallet.accounts.application;

import com.irvinsegura.wallet.accounts.domain.model.BankAccount;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * What the owner sees: enough to tell their accounts apart, never the full CLABE.
 *
 * @param withdrawalsAvailableFrom when its cooling-off period ends
 */
public record BankAccountSummary(UUID id, String bankCode, String clabeLastFour, String holderName,
        Instant createdAt, Instant withdrawalsAvailableFrom) {

    static BankAccountSummary from(BankAccount bankAccount, Duration coolingOff) {
        return new BankAccountSummary(bankAccount.id(), bankAccount.clabe().bankCode(),
                bankAccount.clabe().lastFour(), bankAccount.holderName(), bankAccount.createdAt(),
                bankAccount.createdAt().plus(coolingOff));
    }
}
