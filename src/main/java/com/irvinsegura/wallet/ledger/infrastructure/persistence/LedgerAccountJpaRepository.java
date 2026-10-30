package com.irvinsegura.wallet.ledger.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface LedgerAccountJpaRepository extends JpaRepository<LedgerAccountEntity, UUID> {

    // PostgreSQL locks rows after sorting, so ORDER BY id fixes the lock order across transactions
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from LedgerAccountEntity a where a.id in :ids order by a.id")
    List<LedgerAccountEntity> findAllForUpdate(@Param("ids") Collection<UUID> ids);

    @Query("""
            select new com.irvinsegura.wallet.ledger.infrastructure.persistence.LedgerAccountRow(
                a.id, a.type, a.currency, a.balanceMinor, a.systemCode, a.createdAt, a.version)
            from LedgerAccountEntity a where a.id = :id""")
    Optional<LedgerAccountRow> findRowById(@Param("id") UUID id);

    @Query("""
            select new com.irvinsegura.wallet.ledger.infrastructure.persistence.LedgerAccountRow(
                a.id, a.type, a.currency, a.balanceMinor, a.systemCode, a.createdAt, a.version)
            from LedgerAccountEntity a where a.systemCode = :systemCode and a.currency = :currency""")
    Optional<LedgerAccountRow> findSystemAccountRow(@Param("systemCode") String systemCode,
            @Param("currency") String currency);
}
