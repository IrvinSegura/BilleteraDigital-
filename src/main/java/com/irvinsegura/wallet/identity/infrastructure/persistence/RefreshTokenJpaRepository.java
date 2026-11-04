package com.irvinsegura.wallet.identity.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshTokenEntity t where t.tokenHash = :tokenHash")
    Optional<RefreshTokenEntity> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    // Bulk updates skip @Version, so the version is bumped by hand to keep optimistic locking consistent
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update RefreshTokenEntity t set t.revokedAt = :now, t.version = t.version + 1
            where t.familyId = :familyId and t.revokedAt is null""")
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);

    // Reads ix_refresh_tokens_user_id_active
    @Query("""
            select t from RefreshTokenEntity t
            where t.userId = :userId and t.revokedAt is null and t.expiresAt > :now
            order by t.issuedAt desc""")
    List<RefreshTokenEntity> findLiveByUser(@Param("userId") UUID userId, @Param("now") Instant now);

    @Query("""
            select new com.irvinsegura.wallet.identity.infrastructure.persistence.SessionStartRow(
                t.familyId, min(t.issuedAt))
            from RefreshTokenEntity t where t.familyId in :familyIds group by t.familyId""")
    List<SessionStartRow> sessionStarts(@Param("familyIds") Collection<UUID> familyIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update RefreshTokenEntity t set t.revokedAt = :now, t.version = t.version + 1
            where t.userId = :userId and t.revokedAt is null""")
    int revokeAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            delete from RefreshTokenEntity t
            where t.familyId in (
                select f.familyId from RefreshTokenEntity f group by f.familyId having max(f.expiresAt) <= :now)""")
    int deleteExpiredFamilies(@Param("now") Instant now);
}
