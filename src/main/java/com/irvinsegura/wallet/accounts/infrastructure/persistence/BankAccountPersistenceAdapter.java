package com.irvinsegura.wallet.accounts.infrastructure.persistence;

import com.irvinsegura.wallet.accounts.domain.model.BankAccount;
import com.irvinsegura.wallet.accounts.domain.model.BankAccountAlreadyExistsException;
import com.irvinsegura.wallet.accounts.domain.port.BankAccountRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
class BankAccountPersistenceAdapter implements BankAccountRepository {

    private static final String UNIQUE_ACTIVE_CLABE_INDEX = "uk_bank_accounts_owner_clabe_active";

    private final BankAccountJpaRepository jpaRepository;

    BankAccountPersistenceAdapter(BankAccountJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
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
