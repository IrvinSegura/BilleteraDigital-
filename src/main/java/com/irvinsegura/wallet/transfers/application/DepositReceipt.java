package com.irvinsegura.wallet.transfers.application;

import com.irvinsegura.wallet.shared.domain.Money;
import com.irvinsegura.wallet.transfers.domain.model.Deposit;
import com.irvinsegura.wallet.transfers.domain.model.DepositStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * @param balance           wallet balance right after the deposit was credited; null unless completed
 * @param checkoutUrl       where the payer completes the payment; only while pending
 * @param rejectionReason   why a paid deposit was not credited; only when rejected
 */
public record DepositReceipt(UUID id, DepositStatus status, Money amount, Money balance, String checkoutUrl,
        Instant checkoutExpiresAt, String rejectionReason, Instant createdAt, Instant closedAt) {

    static DepositReceipt from(Deposit deposit) {
        return new DepositReceipt(deposit.id(), deposit.status(), deposit.amount(), deposit.balanceAfter(),
                deposit.checkoutUrl(), deposit.checkoutExpiresAt(), deposit.rejectionReason(), deposit.createdAt(),
                deposit.closedAt());
    }
}
