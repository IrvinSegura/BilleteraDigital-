package com.irvinsegura.wallet.accounts.application;

import com.irvinsegura.wallet.shared.domain.Money;
import java.time.Instant;
import java.util.UUID;

public record AdminWalletView(UUID id, UUID ownerId, Money balance, String status, Instant createdAt) {
}
