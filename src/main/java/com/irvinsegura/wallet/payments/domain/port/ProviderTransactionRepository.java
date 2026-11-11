package com.irvinsegura.wallet.payments.domain.port;

import com.irvinsegura.wallet.payments.domain.model.BalanceTransaction;
import com.irvinsegura.wallet.payments.domain.model.ProviderTransaction;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

    void resolve(String provider, String id, UUID resolvedBy, String reason, Instant now);

    /**
     * Newest first; a null status matches every status.
     */
    List<ProviderTransaction> find(ProviderTransaction.Status status, int limit);

    long count(ProviderTransaction.Status status);
}
