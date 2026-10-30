package com.irvinsegura.wallet.ledger.infrastructure.persistence;

import com.irvinsegura.wallet.ledger.domain.model.LedgerAccount;
import com.irvinsegura.wallet.ledger.domain.port.LedgerAccountRepository;
import java.util.Collection;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Unlocked reads return projections, never managed entities. Otherwise an account read earlier in a transaction
 * would sit in the persistence context with an old version, and locking it later would fail as stale once another
 * transaction had updated it.
 */
@Repository
class LedgerAccountPersistenceAdapter implements LedgerAccountRepository {

    private final LedgerAccountJpaRepository jpaRepository;

    LedgerAccountPersistenceAdapter(LedgerAccountJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<LedgerAccount> findById(UUID id) {
        return jpaRepository.findRowById(id).map(LedgerAccountRow::toDomain);
    }

    @Override
    public List<LedgerAccount> findAllForUpdate(Collection<UUID> ids) {
        return jpaRepository.findAllForUpdate(ids).stream().map(LedgerAccountEntity::toDomain).toList();
    }

    @Override
    public Optional<LedgerAccount> findSystemAccount(String systemCode, Currency currency) {
        return jpaRepository.findSystemAccountRow(systemCode, currency.getCurrencyCode())
                .map(LedgerAccountRow::toDomain);
    }

    @Override
    public void save(LedgerAccount account) {
        jpaRepository.save(LedgerAccountEntity.fromDomain(account));
    }
}
