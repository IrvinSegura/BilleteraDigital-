package com.irvinsegura.wallet.transfers.domain.port;

import com.irvinsegura.wallet.transfers.domain.model.Deposit;
import java.util.Optional;
import java.util.UUID;

public interface DepositRepository {

    void save(Deposit deposit);

    Optional<Deposit> findById(UUID id);
}
