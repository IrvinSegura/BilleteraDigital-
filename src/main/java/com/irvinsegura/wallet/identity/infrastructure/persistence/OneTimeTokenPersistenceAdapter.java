package com.irvinsegura.wallet.identity.infrastructure.persistence;

import com.irvinsegura.wallet.identity.domain.model.OneTimeToken;
import com.irvinsegura.wallet.identity.domain.model.TokenPurpose;
import com.irvinsegura.wallet.identity.domain.model.UserId;
import com.irvinsegura.wallet.identity.domain.port.OneTimeTokenRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class OneTimeTokenPersistenceAdapter implements OneTimeTokenRepository {

    private final OneTimeTokenJpaRepository jpaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    OneTimeTokenPersistenceAdapter(OneTimeTokenJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(OneTimeToken token) {
        // Tokens are always new and have no version column, so persist directly instead of a merge that selects first
        entityManager.persist(OneTimeTokenEntity.fromDomain(token));
        entityManager.flush();
    }

    @Override
    public Optional<OneTimeToken> findByTokenHashForUpdate(String tokenHash) {
        return jpaRepository.findByTokenHashForUpdate(tokenHash).map(OneTimeTokenEntity::toDomain);
    }

    @Override
    public void deleteByUser(UserId userId, TokenPurpose purpose) {
        jpaRepository.deleteByUser(userId.value(), purpose);
    }

    @Override
    public int deleteExpired(Instant now) {
        return jpaRepository.deleteExpired(now);
    }
}
