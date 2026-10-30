package com.irvinsegura.wallet.transfers.application;

import com.irvinsegura.wallet.shared.domain.Money;
import java.time.Instant;
import java.util.UUID;

public record DepositReceipt(UUID transactionId, Money amount, Money balance, Instant createdAt) {
}
