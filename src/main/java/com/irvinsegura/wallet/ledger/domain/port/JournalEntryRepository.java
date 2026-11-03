package com.irvinsegura.wallet.ledger.domain.port;

import com.irvinsegura.wallet.ledger.domain.model.JournalEntry;
import com.irvinsegura.wallet.ledger.domain.model.Movement;
import java.util.List;
import java.util.UUID;

public interface JournalEntryRepository {

    void save(JournalEntry entry);

    boolean existsByTypeAndReference(String type, String reference);

    /**
     * Movements of one account, newest first.
     */
    MovementSlice findMovements(UUID accountId, int page, int size);

    record MovementSlice(List<Movement> items, boolean hasNext) {
    }
}
