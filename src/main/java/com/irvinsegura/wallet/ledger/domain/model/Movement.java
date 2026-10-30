package com.irvinsegura.wallet.ledger.domain.model;

import com.irvinsegura.wallet.shared.domain.Money;
import java.time.Instant;
import java.util.UUID;

/**
 * Read model of a posting together with its journal entry, used for statements.
 */
public record Movement(UUID journalEntryId, String type, String reference, Side side, Money amount,
        Money balanceAfter, Instant postedAt) {
}
