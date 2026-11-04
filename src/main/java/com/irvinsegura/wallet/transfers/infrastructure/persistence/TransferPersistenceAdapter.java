package com.irvinsegura.wallet.transfers.infrastructure.persistence;

import com.irvinsegura.wallet.transfers.domain.model.Transfer;
import com.irvinsegura.wallet.transfers.domain.port.TransferRepository;
import com.irvinsegura.wallet.transfers.domain.port.TransferRepository.RecentRecipient;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
class TransferPersistenceAdapter implements TransferRepository {

    private final TransferJpaRepository jpaRepository;

    TransferPersistenceAdapter(TransferJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(Transfer transfer) {
        jpaRepository.save(TransferEntity.fromDomain(transfer));
    }

    @Override
    public Optional<Transfer> findById(UUID id) {
        return jpaRepository.findById(id).map(TransferEntity::toDomain);
    }

    @Override
    public boolean existsBySenderAndRecipient(UUID senderId, UUID recipientId) {
        return jpaRepository.existsBySenderIdAndRecipientId(senderId, recipientId);
    }

    @Override
    public List<RecentRecipient> recentRecipients(UUID senderId, int limit) {
        return jpaRepository.findRecentRecipients(senderId, PageRequest.of(0, limit)).stream()
                .map(TransferPersistenceAdapter::toRecent)
                .toList();
    }

    @Override
    public Optional<RecentRecipient> paymentsTo(UUID senderId, UUID recipientId) {
        return jpaRepository.findPaymentsTo(senderId, recipientId).stream().findFirst()
                .map(TransferPersistenceAdapter::toRecent);
    }

    private static RecentRecipient toRecent(RecentRecipientRow row) {
        return new RecentRecipient(row.recipientId(), row.recipientEmail(), row.lastSentAt(), row.timesSent());
    }
}
