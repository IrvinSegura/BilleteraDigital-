package com.irvinsegura.wallet.transfers.infrastructure.persistence;

import com.irvinsegura.wallet.transfers.domain.model.Withdrawal;
import com.irvinsegura.wallet.transfers.domain.model.WithdrawalStatus;
import com.irvinsegura.wallet.transfers.domain.port.WithdrawalRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;

@Repository
class WithdrawalPersistenceAdapter implements WithdrawalRepository {

    private final WithdrawalJpaRepository jpaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    WithdrawalPersistenceAdapter(WithdrawalJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(Withdrawal withdrawal) {
        jpaRepository.save(WithdrawalEntity.fromDomain(withdrawal));
    }

    @Override
    public Optional<Withdrawal> findById(UUID id) {
        return jpaRepository.findById(id).map(WithdrawalEntity::toDomain);
    }

    @Override
    public Optional<Withdrawal> findByIdForUpdate(UUID id) {
        WithdrawalEntity entity = entityManager.find(WithdrawalEntity.class, id);
        if (entity == null) {
            return Optional.empty();
        }
        // Refreshing under the lock reloads the row, even if an older copy was read earlier in this transaction
        entityManager.refresh(entity, LockModeType.PESSIMISTIC_WRITE);
        return Optional.of(entity.toDomain());
    }

    @Override
    public List<UUID> findPendingCreatedBefore(Instant createdBefore, int limit) {
        return jpaRepository.findIdsByStatusCreatedBefore(WithdrawalStatus.PENDING, createdBefore, Limit.of(limit));
    }
}
