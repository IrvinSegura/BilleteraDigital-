package com.irvinsegura.wallet.transfers;

import java.time.Instant;
import java.util.UUID;

/**
 * Money moved into or out of a wallet by an administrator; the customer is told why.
 *
 * @param direction  {@code CREDIT} or {@code DEBIT}
 * @param approvedBy who applied it: the requester when it was within their limit, a second administrator otherwise
 * @param ticketId   the support ticket it settles, if any
 */
public record AdjustmentApplied(UUID adjustmentId, UUID userId, UUID walletId, String direction, long amountMinor,
        String currency, String reason, UUID ticketId, UUID requestedBy, UUID approvedBy, Instant occurredAt) {
}
