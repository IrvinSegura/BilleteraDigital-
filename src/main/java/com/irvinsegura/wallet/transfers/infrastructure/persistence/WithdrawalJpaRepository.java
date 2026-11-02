package com.irvinsegura.wallet.transfers.infrastructure.persistence;

import com.irvinsegura.wallet.transfers.domain.model.WithdrawalStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface WithdrawalJpaRepository extends JpaRepository<WithdrawalEntity, UUID> {

    @Query("select w.id from WithdrawalEntity w where w.status = :status and w.createdAt < :before order by w.createdAt")
    List<UUID> findIdsByStatusCreatedBefore(@Param("status") WithdrawalStatus status, @Param("before") Instant before,
            Limit limit);
}
