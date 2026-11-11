package com.irvinsegura.wallet.accounts.infrastructure.persistence;

import com.irvinsegura.wallet.accounts.domain.model.WalletStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface WalletJpaRepository extends JpaRepository<WalletEntity, UUID> {

    List<WalletEntity> findByOwnerIdOrderByCreatedAt(UUID ownerId);

    // Scans the table, which is fine for a support search run by a handful of administrators
    @Query(value = "SELECT * FROM wallets WHERE id::text LIKE :idStart ORDER BY created_at DESC, id LIMIT :limit",
            nativeQuery = true)
    List<WalletEntity> findByIdStart(@Param("idStart") String idStart, @Param("limit") int limit);

    @Query("""
            select w from WalletEntity w
            where (:status is null or w.status = :status)
            order by w.createdAt desc, w.id""")
    Slice<WalletEntity> findPage(@Param("status") WalletStatus status, Pageable page);

    long countByStatus(WalletStatus status);

    Optional<WalletEntity> findByOwnerIdAndCurrency(UUID ownerId, String currency);

    boolean existsByOwnerIdAndCurrency(UUID ownerId, String currency);
}
