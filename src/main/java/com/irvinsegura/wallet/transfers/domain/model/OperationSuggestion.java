package com.irvinsegura.wallet.transfers.domain.model;

import com.irvinsegura.wallet.shared.domain.Money;
import java.time.Instant;
import java.util.UUID;

/**
 * What the support search previews of an operation whose id starts with what is being typed.
 *
 * @param kind {@code transfer}, {@code deposit} or {@code withdrawal}
 */
public record OperationSuggestion(String kind, UUID id, Money amount, Instant createdAt) {
}
