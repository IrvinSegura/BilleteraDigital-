package com.irvinsegura.wallet.accounts.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface WalletJpaRepository extends JpaRepository<WalletEntity, UUID> {

    List<WalletEntity> findByOwnerIdOrderByCreatedAt(UUID ownerId);

    boolean existsByOwnerIdAndCurrency(UUID ownerId, String currency);
}
