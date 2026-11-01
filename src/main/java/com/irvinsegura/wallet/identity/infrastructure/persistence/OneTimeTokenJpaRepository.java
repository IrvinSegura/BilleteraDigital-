package com.irvinsegura.wallet.identity.infrastructure.persistence;

import com.irvinsegura.wallet.identity.domain.model.TokenPurpose;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface OneTimeTokenJpaRepository extends JpaRepository<OneTimeTokenEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from OneTimeTokenEntity t where t.tokenHash = :tokenHash")
    Optional<OneTimeTokenEntity> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from OneTimeTokenEntity t where t.userId = :userId and t.purpose = :purpose")
    int deleteByUser(@Param("userId") UUID userId, @Param("purpose") TokenPurpose purpose);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from OneTimeTokenEntity t where t.expiresAt <= :now")
    int deleteExpired(@Param("now") Instant now);
}
