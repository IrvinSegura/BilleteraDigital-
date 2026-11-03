package com.irvinsegura.wallet.ledger.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface JournalEntryJpaRepository extends JpaRepository<JournalEntryEntity, UUID> {

    boolean existsByTypeAndReference(String type, String reference);
}
