package com.irvinsegura.wallet.payments.infrastructure.persistence;

import com.irvinsegura.wallet.payments.domain.model.Payment;
import com.irvinsegura.wallet.payments.domain.model.PaymentStatus;
import com.irvinsegura.wallet.payments.domain.port.PaymentRepository;
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
class PaymentPersistenceAdapter implements PaymentRepository {

    private final PaymentJpaRepository jpaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    PaymentPersistenceAdapter(PaymentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(Payment payment) {
        jpaRepository.save(PaymentEntity.fromDomain(payment));
    }

    @Override
    public Optional<Payment> findById(UUID id) {
        return jpaRepository.findById(id).map(PaymentEntity::toDomain);
    }

    @Override
    public Optional<Payment> findByIdForUpdate(UUID id) {
        PaymentEntity entity = entityManager.find(PaymentEntity.class, id);
        if (entity == null) {
            return Optional.empty();
        }
        // Refreshing under the lock reloads the row, even if an older copy was read earlier in this transaction
        entityManager.refresh(entity, LockModeType.PESSIMISTIC_WRITE);
        return Optional.of(entity.toDomain());
    }

    @Override
    public List<UUID> findCreatedBefore(Instant createdBefore, int limit) {
        return jpaRepository.findIdsByStatusCreatedBefore(PaymentStatus.CREATED, createdBefore, Limit.of(limit));
    }

    @Override
    public List<UUID> findPendingExpiredBefore(Instant expiredBefore, int limit) {
        return jpaRepository.findIdsByStatusExpiringBefore(PaymentStatus.PENDING, expiredBefore, Limit.of(limit));
    }
}
