package com.irvinsegura.wallet.transfers.domain.port;

import com.irvinsegura.wallet.transfers.domain.model.Transfer;
import java.util.Optional;
import java.util.UUID;

public interface TransferRepository {

    void save(Transfer transfer);

    Optional<Transfer> findById(UUID id);
}
