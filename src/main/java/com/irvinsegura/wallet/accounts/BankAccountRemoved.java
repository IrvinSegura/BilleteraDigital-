package com.irvinsegura.wallet.accounts;

import java.time.Instant;
import java.util.UUID;

public record BankAccountRemoved(UUID bankAccountId, UUID ownerId, String clabeLastFour, Instant occurredAt) {
}
