package com.irvinsegura.wallet.transfers;

import com.irvinsegura.wallet.shared.domain.Money;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A customer's operations as support sees them while attending the customer: what, when and how it ended, never a
 * balance, a bank account or the other party.
 */
public interface CustomerOperations {

    /**
     * The newest deposits, withdrawals and transfers of the customer, sent or received.
     */
    List<OperationBrief> recent(UUID userId, int limit);

    /**
     * One of the customer's operations; empty when it isn't theirs.
     */
    Optional<OperationBrief> find(UUID userId, UUID operationId);

    /**
     * @param kind     {@code deposit}, {@code withdrawal}, {@code transfer_out}, {@code transfer_in},
     *                 {@code adjustment_credit} or {@code adjustment_debit}
     * @param status   the operation's own status ({@code COMPLETED} for a transfer, which never waits)
     * @param reason   why it was rejected or failed; {@code null} otherwise
     * @param closedAt when it stopped waiting; {@code null} while it waits
     */
    record OperationBrief(String kind, UUID id, String status, Money amount, String reason, Instant createdAt,
            Instant closedAt) {
    }
}
