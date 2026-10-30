package com.irvinsegura.wallet.accounts.application;

import com.irvinsegura.wallet.shared.domain.Money;
import java.time.Instant;
import java.util.UUID;

public record WalletSummary(UUID id, Money balance, String status, Instant createdAt) {
}
