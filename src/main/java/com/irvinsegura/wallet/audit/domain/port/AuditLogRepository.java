package com.irvinsegura.wallet.audit.domain.port;

import com.irvinsegura.wallet.audit.domain.model.AuditEntry;
import java.util.List;
import java.util.UUID;

public interface AuditLogRepository {

    /**
     * Stores the entry unless one with the same id already exists.
     */
    void append(AuditEntry entry);

    /**
     * Newest first; null filters match everything.
     */
    AuditSlice find(UUID actorId, UUID targetId, int page, int size);

    record AuditSlice(List<AuditEntry> items, boolean hasNext) {
    }
}
