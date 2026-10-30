package com.irvinsegura.wallet.transfers.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface TransferJpaRepository extends JpaRepository<TransferEntity, UUID> {
}
