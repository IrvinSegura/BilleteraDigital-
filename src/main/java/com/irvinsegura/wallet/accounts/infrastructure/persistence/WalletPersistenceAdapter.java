package com.irvinsegura.wallet.accounts.infrastructure.persistence;

import com.irvinsegura.wallet.accounts.domain.model.Wallet;
import com.irvinsegura.wallet.accounts.domain.model.WalletAlreadyExistsException;
import com.irvinsegura.wallet.accounts.domain.model.WalletStatus;
import com.irvinsegura.wallet.accounts.domain.port.WalletRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Repository;

@Repository
class WalletPersistenceAdapter implements WalletRepository {

    private static final String UNIQUE_OWNER_CURRENCY_CONSTRAINT = "uk_wallets_owner_currency";

    private final WalletJpaRepository jpaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    WalletPersistenceAdapter(WalletJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<Wallet> findById(UUID id) {
        return jpaRepository.findById(id).map(WalletEntity::toDomain);
    }

    @Override
    public Optional<Wallet> findByIdForUpdate(UUID id) {
        return findLocked(id, LockModeType.PESSIMISTIC_WRITE);
    }

    // PESSIMISTIC_READ is SELECT ... FOR SHARE in PostgreSQL
    @Override
    public Optional<Wallet> findByIdForShare(UUID id) {
        return findLocked(id, LockModeType.PESSIMISTIC_READ);
    }

    private Optional<Wallet> findLocked(UUID id, LockModeType lockMode) {
        WalletEntity entity = entityManager.find(WalletEntity.class, id);
        if (entity == null) {
            return Optional.empty();
        }
        // Refreshing under the lock reloads the row, even if an older copy was read earlier in this transaction
        entityManager.refresh(entity, lockMode);
        return Optional.of(entity.toDomain());
    }

    @Override
    public List<Wallet> findByOwner(UUID ownerId) {
        return jpaRepository.findByOwnerIdOrderByCreatedAt(ownerId).stream().map(WalletEntity::toDomain).toList();
    }

    @Override
    public List<Wallet> findByIdStart(String idStart, int limit) {
        return jpaRepository.findByIdStart(idStart + "%", limit).stream().map(WalletEntity::toDomain).toList();
    }

    @Override
    public WalletSlice findPage(WalletStatus status, int page, int size) {
        Slice<WalletEntity> slice = jpaRepository.findPage(status, PageRequest.of(page, size));
        return new WalletSlice(slice.getContent().stream().map(WalletEntity::toDomain).toList(), slice.hasNext());
    }

    @Override
    public WalletCounts count() {
        return new WalletCounts(jpaRepository.count(), jpaRepository.countByStatus(WalletStatus.FROZEN));
    }

    @Override
    public Optional<Wallet> findByOwnerAndCurrency(UUID ownerId, Currency currency) {
        return jpaRepository.findByOwnerIdAndCurrency(ownerId, currency.getCurrencyCode())
                .map(WalletEntity::toDomain);
    }

    @Override
    public boolean existsByOwnerAndCurrency(UUID ownerId, Currency currency) {
        return jpaRepository.existsByOwnerIdAndCurrency(ownerId, currency.getCurrencyCode());
    }

    @Override
    public void save(Wallet wallet) {
        try {
            // Flush now so a concurrent request opening the same currency fails here, not at commit time
            jpaRepository.saveAndFlush(WalletEntity.fromDomain(wallet));
        } catch (DataIntegrityViolationException ex) {
            if (ex.getCause() instanceof ConstraintViolationException violation
                    && UNIQUE_OWNER_CURRENCY_CONSTRAINT.equals(violation.getConstraintName())) {
                throw new WalletAlreadyExistsException(wallet.currency());
            }
            throw ex;
        }
    }
}
