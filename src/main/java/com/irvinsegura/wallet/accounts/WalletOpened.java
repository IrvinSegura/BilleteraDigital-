package com.irvinsegura.wallet.accounts;

import java.time.Instant;
import java.util.UUID;

public record WalletOpened(UUID walletId, UUID ownerId, String currency, Instant occurredAt) {
}
