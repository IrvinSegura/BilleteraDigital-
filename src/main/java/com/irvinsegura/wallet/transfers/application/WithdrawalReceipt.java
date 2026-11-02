package com.irvinsegura.wallet.transfers.application;

import com.irvinsegura.wallet.shared.domain.Money;
import com.irvinsegura.wallet.transfers.domain.model.Withdrawal;
import com.irvinsegura.wallet.transfers.domain.model.WithdrawalStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * @param balance       wallet balance right after the money left it
 * @param failureReason why the bank refused the payout; only when failed
 */
public record WithdrawalReceipt(UUID id, WithdrawalStatus status, Money amount, Money balance, UUID bankAccountId,
        String failureReason, Instant createdAt, Instant closedAt) {

    static WithdrawalReceipt from(Withdrawal withdrawal) {
        return new WithdrawalReceipt(withdrawal.id(), withdrawal.status(), withdrawal.amount(),
                withdrawal.balanceAfter(), withdrawal.bankAccountId(), withdrawal.failureReason(),
                withdrawal.createdAt(), withdrawal.closedAt());
    }
}
