package com.irvinsegura.wallet.identity.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface UserJpaRepository extends JpaRepository<UserEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserEntity u where u.email = :email")
    Optional<UserEntity> findByEmailForUpdate(@Param("email") String email);

    Optional<UserEntity> findByEmail(String email);

    boolean existsByEmail(String email);
}
