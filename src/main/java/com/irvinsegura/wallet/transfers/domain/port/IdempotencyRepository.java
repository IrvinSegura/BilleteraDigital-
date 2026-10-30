package com.irvinsegura.wallet.transfers.domain.port;

import com.irvinsegura.wallet.transfers.domain.model.IdempotencyRecord;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRepository {

    Optional<IdempotencyRecord> find(UUID userId, String key);

    /**
     * Inserts the record right away. A concurrent request with the same key waits here until the first one commits
     * or rolls back.
     *
     * @throws com.irvinsegura.wallet.transfers.domain.model.IdempotencyKeyTakenException if a committed request
     *                                                                                    already holds the key
     */
    void claim(IdempotencyRecord record);

    /**
     * @return how many keys were deleted
     */
    int deleteCreatedBefore(Instant cutoff);
}
