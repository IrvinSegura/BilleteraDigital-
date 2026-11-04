package com.irvinsegura.wallet.transfers.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface TransferJpaRepository extends JpaRepository<TransferEntity, UUID> {

    boolean existsBySenderIdAndRecipientId(UUID senderId, UUID recipientId);

    // Reads the sender's transfers through ix_transfers_sender_recipient
    @Query("""
            select new com.irvinsegura.wallet.transfers.infrastructure.persistence.RecentRecipientRow(
                t.recipientId, t.recipientEmail, max(t.createdAt), count(t))
            from TransferEntity t
            where t.senderId = :senderId
            group by t.recipientId, t.recipientEmail
            order by max(t.createdAt) desc""")
    List<RecentRecipientRow> findRecentRecipients(@Param("senderId") UUID senderId, Pageable pageable);

    @Query("""
            select new com.irvinsegura.wallet.transfers.infrastructure.persistence.RecentRecipientRow(
                t.recipientId, t.recipientEmail, max(t.createdAt), count(t))
            from TransferEntity t
            where t.senderId = :senderId and t.recipientId = :recipientId
            group by t.recipientId, t.recipientEmail""")
    List<RecentRecipientRow> findPaymentsTo(@Param("senderId") UUID senderId,
            @Param("recipientId") UUID recipientId);
}
