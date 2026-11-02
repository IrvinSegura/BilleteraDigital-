package com.irvinsegura.wallet.accounts.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface BankAccountJpaRepository extends JpaRepository<BankAccountEntity, UUID> {

    List<BankAccountEntity> findByOwnerIdAndRemovedAtIsNullOrderByCreatedAt(UUID ownerId);
}
