package com.irvinsegura.wallet.transfers;

import java.time.Instant;
import java.util.UUID;

/**
 * An adjustment waiting for approval was turned down, or withdrawn by its requester; no money moved.
 */
public record AdjustmentRejected(UUID adjustmentId, UUID userId, UUID rejectedBy, String reason, Instant occurredAt) {
}
