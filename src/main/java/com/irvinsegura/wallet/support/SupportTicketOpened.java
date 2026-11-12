package com.irvinsegura.wallet.support;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when an agent passes a customer's problem to the administrators.
 *
 * @param category      {@code OPERATION}, {@code ACCOUNT_ACCESS}, {@code IDENTITY} or {@code OTHER}
 * @param operationKind {@code null} unless it is about an operation, as is {@code operationId}
 */
public record SupportTicketOpened(UUID ticketId, UUID conversationId, UUID userId, UUID agentId, String category,
        String operationKind, UUID operationId, Instant occurredAt) {
}
