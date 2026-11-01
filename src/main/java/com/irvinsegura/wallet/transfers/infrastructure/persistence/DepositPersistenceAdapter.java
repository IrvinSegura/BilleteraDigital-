package com.irvinsegura.wallet.transfers.infrastructure.persistence;

import com.irvinsegura.wallet.transfers.domain.model.Deposit;
import com.irvinsegura.wallet.transfers.domain.port.DepositRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class DepositPersistenceAdapter implements DepositRepository {

    private final DepositJpaRepository jpaRepository;

    @PersistenceContext
    private EntityManager entityManager;

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

    @Override
    public Optional<Deposit> findByIdForUpdate(UUID id) {
        DepositEntity entity = entityManager.find(DepositEntity.class, id);
        if (entity == null) {
            return Optional.empty();
        }
        // Refreshing under the lock reloads the row, even if an older copy was read earlier in this transaction
        entityManager.refresh(entity, LockModeType.PESSIMISTIC_WRITE);
        return Optional.of(entity.toDomain());
    }
}
