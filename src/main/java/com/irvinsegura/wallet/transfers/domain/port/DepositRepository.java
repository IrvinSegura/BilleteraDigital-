package com.irvinsegura.wallet.transfers.domain.port;

import com.irvinsegura.wallet.transfers.domain.model.Deposit;
import java.util.Optional;
import java.util.UUID;

public interface DepositRepository {

    void save(Deposit deposit);

    Optional<Deposit> findById(UUID id);

    /**
     * Locks the row until the transaction ends, so the request that started a deposit and the listener confirming
     * its payment never credit it twice.
     */
    Optional<Deposit> findByIdForUpdate(UUID id);
}
