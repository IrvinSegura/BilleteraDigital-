package com.irvinsegura.wallet.transfers.infrastructure.persistence;

import com.irvinsegura.wallet.transfers.domain.model.Transfer;
import com.irvinsegura.wallet.transfers.domain.port.TransferRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class TransferPersistenceAdapter implements TransferRepository {

    private final TransferJpaRepository jpaRepository;

    TransferPersistenceAdapter(TransferJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(Transfer transfer) {
        jpaRepository.save(TransferEntity.fromDomain(transfer));
    }

    @Override
    public Optional<Transfer> findById(UUID id) {
        return jpaRepository.findById(id).map(TransferEntity::toDomain);
    }
}
