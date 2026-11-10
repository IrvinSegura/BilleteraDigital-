package com.irvinsegura.wallet.ledger;

import java.time.Instant;
import java.util.Set;

/**
 * Which of an account's movements to list. Every criterion is optional: {@code null} (or an empty set of types)
 * lets any movement through.
 *
 * @param from posted at or after this instant
 * @param to posted before this instant
 * @param types entry types, such as {@code DEPOSIT} or {@code TRANSFER}
 * @param direction whether the movement raised or lowered the balance
 * @param referencePrefix start of the entry's reference (the id of the operation behind it), any case
 * @param references exact references; with {@code referencePrefix}, a movement may match either one
 */
public record MovementFilter(Instant from, Instant to, Set<String> types, AccountMovement.Direction direction,
        String referencePrefix, Set<String> references) {

    public MovementFilter {
        types = types == null ? Set.of() : Set.copyOf(types);
        references = references == null ? null : Set.copyOf(references);
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("from must not be after to");
        }
    }

    public static MovementFilter none() {
        return new MovementFilter(null, null, Set.of(), null, null, null);
    }
}
