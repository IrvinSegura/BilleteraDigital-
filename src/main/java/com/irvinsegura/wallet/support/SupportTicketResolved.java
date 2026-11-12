package com.irvinsegura.wallet.support;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when an administrator settles a ticket; {@code resolution} is what the customer is told.
 *
 * @param openedBy the agent who escalated it, who is told too
 */
public record SupportTicketResolved(UUID ticketId, UUID conversationId, UUID userId, UUID openedBy, UUID adminId,
        String resolution, Instant occurredAt) {
}
