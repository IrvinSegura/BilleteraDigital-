package com.irvinsegura.wallet.transfers.application;

import com.irvinsegura.wallet.shared.domain.Money;
import com.irvinsegura.wallet.transfers.domain.model.Withdrawal;
import java.time.Instant;
import java.util.UUID;

public record WithdrawalReceipt(UUID id, Money amount, Money balance, Instant createdAt) {

    static WithdrawalReceipt from(Withdrawal withdrawal) {
        return new WithdrawalReceipt(withdrawal.id(), withdrawal.amount(), withdrawal.balanceAfter(),
                withdrawal.createdAt());
    }
}
