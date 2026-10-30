package com.irvinsegura.wallet.transfers.infrastructure.persistence;

import com.irvinsegura.wallet.transfers.domain.model.IdempotencyKeyTakenException;
import com.irvinsegura.wallet.transfers.domain.model.IdempotencyRecord;
import com.irvinsegura.wallet.transfers.domain.port.IdempotencyRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
class IdempotencyPersistenceAdapter implements IdempotencyRepository {

    private static final String PRIMARY_KEY_CONSTRAINT = "pk_idempotency_keys";

    private final IdempotencyKeyJpaRepository jpaRepository;

    IdempotencyPersistenceAdapter(IdempotencyKeyJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<IdempotencyRecord> find(UUID userId, String key) {
        return jpaRepository.findById(new IdempotencyKeyEntity.Key(userId, key)).map(IdempotencyKeyEntity::toDomain);
    }

    @Override
    public void claim(IdempotencyRecord record) {
        try {
            // Flush now: the insert must reach the database before any money moves
            jpaRepository.saveAndFlush(IdempotencyKeyEntity.fromDomain(record));
        } catch (DataIntegrityViolationException ex) {
            if (ex.getCause() instanceof ConstraintViolationException violation
                    && PRIMARY_KEY_CONSTRAINT.equals(violation.getConstraintName())) {
                throw new IdempotencyKeyTakenException();
            }
            throw ex;
        }
    }

    @Override
    public int deleteCreatedBefore(Instant cutoff) {
        return jpaRepository.deleteCreatedBefore(cutoff);
    }
}
