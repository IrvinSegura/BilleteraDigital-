package com.irvinsegura.wallet.accounts.infrastructure.persistence;

import com.irvinsegura.wallet.accounts.domain.model.BankAccount;
import com.irvinsegura.wallet.accounts.domain.model.BankAccountAlreadyExistsException;
import com.irvinsegura.wallet.accounts.domain.port.BankAccountRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class BankAccountPersistenceAdapter implements BankAccountRepository {

    private static final String UNIQUE_ACTIVE_CLABE_INDEX = "uk_bank_accounts_owner_clabe_active";

    private final BankAccountJpaRepository jpaRepository;
    private final JdbcClient jdbcClient;

    BankAccountPersistenceAdapter(BankAccountJpaRepository jpaRepository, JdbcClient jdbcClient) {
        this.jpaRepository = jpaRepository;
        this.jdbcClient = jdbcClient;
    }

    // An advisory lock rather than a row lock: an owner's first bank account has no row to lock yet. Released at
    // commit; two owners sharing a hash only wait for each other, never see each other's rows
    @Override
    public void lockOwner(UUID ownerId) {
        jdbcClient.sql("SELECT 1 FROM pg_advisory_xact_lock(hashtextextended(:key, 0))")
                .param("key", "bank_accounts:" + ownerId)
                .query(Integer.class)
                .single();
    }

    @Override
    public void save(BankAccount bankAccount) {
        try {
            // Flush now so the same CLABE added twice at once fails here, not at commit time
            jpaRepository.saveAndFlush(BankAccountEntity.fromDomain(bankAccount));
        } catch (DataIntegrityViolationException ex) {
            if (ex.getCause() instanceof ConstraintViolationException violation
                    && UNIQUE_ACTIVE_CLABE_INDEX.equals(violation.getConstraintName())) {
                throw new BankAccountAlreadyExistsException();
            }
            throw ex;
        }
    }

    @Override
    public Optional<BankAccount> findById(UUID id) {
        return jpaRepository.findById(id).map(BankAccountEntity::toDomain);
    }

    @Override
    public List<BankAccount> findActiveByOwner(UUID ownerId) {
        return jpaRepository.findByOwnerIdAndRemovedAtIsNullOrderByCreatedAt(ownerId).stream()
                .map(BankAccountEntity::toDomain)
                .toList();
    }
}
