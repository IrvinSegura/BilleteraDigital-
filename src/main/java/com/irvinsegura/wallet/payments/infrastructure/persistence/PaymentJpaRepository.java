package com.irvinsegura.wallet.payments.infrastructure.persistence;

import com.irvinsegura.wallet.payments.domain.model.PaymentStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PaymentJpaRepository extends JpaRepository<PaymentEntity, UUID> {

    @Query("select p.id from PaymentEntity p where p.status = :status and p.createdAt < :before order by p.createdAt")
    List<UUID> findIdsByStatusCreatedBefore(@Param("status") PaymentStatus status, @Param("before") Instant before,
            Limit limit);

    @Query("select p.id from PaymentEntity p where p.status = :status and p.expiresAt < :before order by p.expiresAt")
    List<UUID> findIdsByStatusExpiringBefore(@Param("status") PaymentStatus status, @Param("before") Instant before,
            Limit limit);

    Optional<PaymentEntity> findByProviderAndProviderPaymentId(String provider, String providerPaymentId);

    @Query("select min(p.createdAt) from PaymentEntity p where p.provider = :provider")
    Instant findEarliestCreatedAt(@Param("provider") String provider);
}
