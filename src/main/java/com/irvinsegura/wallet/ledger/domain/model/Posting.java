package com.irvinsegura.wallet.ledger.domain.model;

import com.irvinsegura.wallet.shared.domain.Money;
import java.util.UUID;

/**
 * Applied side of a journal entry. {@code balanceAfter} makes statements and audits possible without replaying
 * the account's history.
 */
public record Posting(UUID id, UUID accountId, Side side, Money amount, Money balanceAfter) {
}
