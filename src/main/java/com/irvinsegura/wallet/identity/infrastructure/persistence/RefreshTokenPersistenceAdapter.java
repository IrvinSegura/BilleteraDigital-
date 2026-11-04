package com.irvinsegura.wallet.identity.infrastructure.persistence;

import com.irvinsegura.wallet.identity.domain.model.RefreshToken;
import com.irvinsegura.wallet.identity.domain.model.UserId;
import com.irvinsegura.wallet.identity.domain.port.RefreshTokenRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

@Repository
class RefreshTokenPersistenceAdapter implements RefreshTokenRepository {

    private final RefreshTokenJpaRepository jpaRepository;

    RefreshTokenPersistenceAdapter(RefreshTokenJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<RefreshToken> findByTokenHashForUpdate(String tokenHash) {
        return jpaRepository.findByTokenHashForUpdate(tokenHash).map(RefreshTokenEntity::toDomain);
    }

    @Override
    public void save(RefreshToken token) {
        jpaRepository.save(RefreshTokenEntity.fromDomain(token));
    }

    @Override
    public void revokeFamily(UUID familyId, Instant now) {
        jpaRepository.revokeFamily(familyId, now);
    }

    @Override
    public List<RefreshToken> findLiveByUser(UserId userId, Instant now) {
        return jpaRepository.findLiveByUser(userId.value(), now).stream().map(RefreshTokenEntity::toDomain).toList();
    }

    @Override
    public Map<UUID, Instant> sessionStarts(Collection<UUID> familyIds) {
        if (familyIds.isEmpty()) {
            return Map.of();
        }
        return jpaRepository.sessionStarts(familyIds).stream()
                .collect(Collectors.toMap(SessionStartRow::familyId, SessionStartRow::startedAt));
    }

    @Override
    public void revokeAllForUser(UserId userId, Instant now) {
        jpaRepository.revokeAllForUser(userId.value(), now);
    }

    @Override
    public int deleteExpiredFamilies(Instant now) {
        return jpaRepository.deleteExpiredFamilies(now);
    }
}
