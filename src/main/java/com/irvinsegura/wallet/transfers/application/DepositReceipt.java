package com.irvinsegura.wallet.transfers.application;

import com.irvinsegura.wallet.shared.domain.Money;
import com.irvinsegura.wallet.transfers.domain.model.Deposit;
import java.time.Instant;
import java.util.UUID;

public record DepositReceipt(UUID id, Money amount, Money balance, Instant createdAt) {

    static DepositReceipt from(Deposit deposit) {
        return new DepositReceipt(deposit.id(), deposit.amount(), deposit.balanceAfter(), deposit.createdAt());
    }
}
