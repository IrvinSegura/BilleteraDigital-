package com.irvinsegura.wallet.audit.domain.port;

import com.irvinsegura.wallet.audit.domain.model.AuditEntry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AuditLogRepository {

    /**
     * Stores the entry unless one with the same id already exists.
     */
    void append(AuditEntry entry);

    /**
     * Newest first; null filters match everything.
     *
     * @param from earliest moment included
     * @param to   first moment no longer included
     */
    AuditSlice find(UUID actorId, UUID targetId, String action, Instant from, Instant to, int page, int size);

    record AuditSlice(List<AuditEntry> items, boolean hasNext) {
    }
}
