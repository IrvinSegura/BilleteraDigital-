package com.irvinsegura.wallet.transfers.domain.model;

import com.irvinsegura.wallet.shared.domain.Money;
import java.time.Instant;
import java.util.UUID;

/**
 * A deposit waiting for its payment or a withdrawal waiting for its payout, as support's queue shows it.
 *
 * @param kind {@code deposit} or {@code withdrawal}
 */
public record PendingOperation(String kind, UUID id, UUID userId, UUID walletId, Money amount, Instant createdAt) {
}
