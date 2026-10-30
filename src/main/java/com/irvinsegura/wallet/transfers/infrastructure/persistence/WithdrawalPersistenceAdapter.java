package com.irvinsegura.wallet.transfers.infrastructure.persistence;

import com.irvinsegura.wallet.transfers.domain.model.Withdrawal;
import com.irvinsegura.wallet.transfers.domain.port.WithdrawalRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class WithdrawalPersistenceAdapter implements WithdrawalRepository {

    private final WithdrawalJpaRepository jpaRepository;

    WithdrawalPersistenceAdapter(WithdrawalJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(Withdrawal withdrawal) {
        jpaRepository.save(WithdrawalEntity.fromDomain(withdrawal));
    }

    @Override
    public Optional<Withdrawal> findById(UUID id) {
        return jpaRepository.findById(id).map(WithdrawalEntity::toDomain);
    }
}
