package com.irvinsegura.wallet.transfers.domain.port;

import com.irvinsegura.wallet.transfers.domain.model.Withdrawal;
import java.util.Optional;
import java.util.UUID;

public interface WithdrawalRepository {

    void save(Withdrawal withdrawal);

    Optional<Withdrawal> findById(UUID id);
}
