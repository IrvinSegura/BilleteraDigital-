package com.irvinsegura.wallet.transfers.infrastructure.persistence;

import com.irvinsegura.wallet.transfers.domain.model.Deposit;
import com.irvinsegura.wallet.transfers.domain.port.DepositRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class DepositPersistenceAdapter implements DepositRepository {

    private final DepositJpaRepository jpaRepository;

    DepositPersistenceAdapter(DepositJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(Deposit deposit) {
        jpaRepository.save(DepositEntity.fromDomain(deposit));
    }

    @Override
    public Optional<Deposit> findById(UUID id) {
        return jpaRepository.findById(id).map(DepositEntity::toDomain);
    }
}
