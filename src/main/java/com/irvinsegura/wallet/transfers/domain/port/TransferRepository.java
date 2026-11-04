package com.irvinsegura.wallet.transfers.domain.port;

import com.irvinsegura.wallet.transfers.domain.model.Transfer;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransferRepository {

    void save(Transfer transfer);

    Optional<Transfer> findById(UUID id);

    /**
     * Whether the sender has ever sent money to this recipient.
     */
    boolean existsBySenderAndRecipient(UUID senderId, UUID recipientId);

    /**
     * The people the sender has paid, the most recently paid first.
     */
    List<RecentRecipient> recentRecipients(UUID senderId, int limit);

    /**
     * What the sender has paid this recipient so far; empty if never.
     */
    Optional<RecentRecipient> paymentsTo(UUID senderId, UUID recipientId);

    record RecentRecipient(UUID recipientId, String email, Instant lastSentAt, long timesSent) {
    }
}
