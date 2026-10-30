package com.irvinsegura.wallet.transfers.domain.model;

import com.irvinsegura.wallet.shared.domain.Money;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Completed payout to the user's bank. {@code balanceAfter} is kept so a replayed request returns exactly the
 * original response.
 */
public record Withdrawal(UUID id, UUID userId, UUID walletId, Money amount, Money balanceAfter, UUID journalEntryId,
        Instant createdAt) {

    public Withdrawal {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(walletId, "walletId must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(balanceAfter, "balanceAfter must not be null");
        Objects.requireNonNull(journalEntryId, "journalEntryId must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}
