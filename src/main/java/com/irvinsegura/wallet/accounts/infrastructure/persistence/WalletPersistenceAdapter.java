package com.irvinsegura.wallet.accounts.infrastructure.persistence;

import com.irvinsegura.wallet.accounts.domain.model.Wallet;
import com.irvinsegura.wallet.accounts.domain.model.WalletAlreadyExistsException;
import com.irvinsegura.wallet.accounts.domain.port.WalletRepository;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
class WalletPersistenceAdapter implements WalletRepository {

    private static final String UNIQUE_OWNER_CURRENCY_CONSTRAINT = "uk_wallets_owner_currency";

    private final WalletJpaRepository jpaRepository;

    WalletPersistenceAdapter(WalletJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<Wallet> findById(UUID id) {
        return jpaRepository.findById(id).map(WalletEntity::toDomain);
    }

    @Override
    public List<Wallet> findByOwner(UUID ownerId) {
        return jpaRepository.findByOwnerIdOrderByCreatedAt(ownerId).stream().map(WalletEntity::toDomain).toList();
    }

    @Override
    public Optional<Wallet> findByOwnerAndCurrency(UUID ownerId, Currency currency) {
        return jpaRepository.findByOwnerIdAndCurrency(ownerId, currency.getCurrencyCode()).map(WalletEntity::toDomain);
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
