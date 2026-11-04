package com.irvinsegura.wallet.ledger.domain.port;

import com.irvinsegura.wallet.ledger.domain.model.JournalEntry;
import com.irvinsegura.wallet.ledger.domain.model.Movement;
import com.irvinsegura.wallet.ledger.domain.model.Side;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface JournalEntryRepository {

    void save(JournalEntry entry);

    boolean existsByTypeAndReference(String type, String reference);

    /**
     * Movements of one account, newest first.
     */
    MovementSlice findMovements(UUID accountId, int page, int size);

    /**
     * Movements of one account that match {@code query}, newest first.
     */
    MovementSlice findMovements(UUID accountId, MovementQuery query, int page, int size);

    /**
     * The posting a journal entry made on one account, with its entry.
     */
    Optional<Movement> findMovement(UUID accountId, UUID journalEntryId);

    /**
     * Amounts of one account's postings on each side, posted from {@code from} (inclusive) to {@code to}
     * (exclusive).
     */
    SideTotals sumBySide(UUID accountId, Instant from, Instant to);

    /**
     * Balance after the account's last posting before {@code before}; empty when it had none yet.
     */
    Optional<Long> balanceBefore(UUID accountId, Instant before);

    /**
     * Balance after the account's last posting of each day that had postings from {@code from} (inclusive) to
     * {@code to} (exclusive), oldest day first; days are counted in {@code zone}.
     */
    List<DayClose> closingBalances(UUID accountId, Instant from, Instant to, ZoneId zone);

    record MovementSlice(List<Movement> items, boolean hasNext) {
    }

    /**
     * Criteria of {@link #findMovements(UUID, MovementQuery, int, int)}; {@code null} (or empty {@code types})
     * matches anything.
     */
    record MovementQuery(Instant from, Instant to, Set<String> types, Side side, String referencePrefix) {

        public MovementQuery {
            types = types == null ? Set.of() : Set.copyOf(types);
        }
    }

    record SideTotals(long debitMinor, long creditMinor) {
    }

    record DayClose(LocalDate day, long balanceMinor) {
    }
}
