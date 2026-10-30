package com.irvinsegura.wallet.ledger.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Outcome of one reconciliation of the whole ledger, taken from a single consistent snapshot.
 *
 * @param triggeredBy administrator who started a manual run; null for scheduled runs
 */
public record ReconciliationRun(UUID id, Trigger trigger, UUID triggeredBy, Instant startedAt, Instant finishedAt,
        long accountsChecked, long entriesChecked, List<Discrepancy> discrepancies) {

    public enum Trigger {
        SCHEDULED,
        MANUAL
    }

    public ReconciliationRun {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(trigger, "trigger must not be null");
        Objects.requireNonNull(startedAt, "startedAt must not be null");
        Objects.requireNonNull(finishedAt, "finishedAt must not be null");
        discrepancies = List.copyOf(discrepancies);
    }

    public boolean isConsistent() {
        return discrepancies.isEmpty();
    }
}
