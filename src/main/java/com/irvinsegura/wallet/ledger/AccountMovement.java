package com.irvinsegura.wallet.ledger;

import com.irvinsegura.wallet.shared.domain.Money;
import java.time.Instant;
import java.util.UUID;

/**
 * One posting seen from its account: {@code IN} raised the balance, {@code OUT} lowered it.
 */
public record AccountMovement(UUID journalEntryId, String type, String reference, Direction direction, Money amount,
        Money balanceAfter, Instant postedAt) {

    public enum Direction {
        IN,
        OUT
    }
}
