package com.irvinsegura.wallet.payments.domain.port;

import com.irvinsegura.wallet.payments.domain.model.BalanceTransaction;
import com.irvinsegura.wallet.payments.domain.model.ProviderTransaction;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ProviderTransactionRepository {

    /**
     * Stores it as pending unless it was read before. Returns whether it was new.
     */
    boolean recordIfNew(String provider, BalanceTransaction transaction, Instant now);

    Optional<Instant> findLatestCreatedAt(String provider);

    /**
     * Ids of pending transactions, oldest first.
     */
    List<String> findPendingIds(String provider, int limit);

    /**
     * Locks the row until the transaction ends, so two instances never record the same transaction twice.
     */
    Optional<ProviderTransaction> findByIdForUpdate(String provider, String id);

    void settle(String provider, String id, ProviderTransaction.Status status, String note, Instant now);

    /**
     * Newest first.
     */
    List<ProviderTransaction> findByStatus(ProviderTransaction.Status status, int limit);
}
