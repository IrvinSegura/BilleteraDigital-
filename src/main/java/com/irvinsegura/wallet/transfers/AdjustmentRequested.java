package com.irvinsegura.wallet.transfers;

import java.time.Instant;
import java.util.UUID;

/**
 * An administrator asked to adjust a wallet by more than they may alone: it waits for a second administrator.
 *
 * @param direction {@code CREDIT} or {@code DEBIT}
 * @param ticketId  the support ticket it settles, if any
 */
public record AdjustmentRequested(UUID adjustmentId, UUID userId, UUID walletId, String direction, long amountMinor,
        String currency, String reason, UUID ticketId, UUID requestedBy, Instant occurredAt) {
}
