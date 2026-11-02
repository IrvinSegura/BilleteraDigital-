package com.irvinsegura.wallet.transfers.domain.port;

import com.irvinsegura.wallet.transfers.domain.model.Withdrawal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WithdrawalRepository {

    void save(Withdrawal withdrawal);

    Optional<Withdrawal> findById(UUID id);

    /**
     * Locks the row until the transaction ends, so the request that made a withdrawal and the payout job never
     * settle it twice.
     */
    Optional<Withdrawal> findByIdForUpdate(UUID id);

    /**
     * Ids of withdrawals still pending that were requested before {@code createdBefore}, oldest first.
     */
    List<UUID> findPendingCreatedBefore(Instant createdBefore, int limit);
}
