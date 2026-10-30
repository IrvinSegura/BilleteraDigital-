package com.irvinsegura.wallet.ledger.infrastructure.persistence;

import com.irvinsegura.wallet.ledger.domain.model.Movement;
import com.irvinsegura.wallet.ledger.domain.model.Side;
import com.irvinsegura.wallet.shared.domain.Money;
import java.time.Instant;
import java.util.UUID;

/**
 * Query projection; public because Hibernate instantiates it from a JPQL constructor expression.
 */
public record MovementRow(UUID journalEntryId, String type, String reference, Side side, long amountMinor,
        String currency, long balanceAfterMinor, Instant postedAt) {

    Movement toDomain() {
        return new Movement(journalEntryId, type, reference, side, Money.ofMinor(amountMinor, currency),
                Money.ofMinor(balanceAfterMinor, currency), postedAt);
    }
}
