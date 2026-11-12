package com.irvinsegura.wallet.support;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when the agent attending a conversation closes it, which is when the user is asked how it went.
 */
public record SupportCaseResolved(UUID conversationId, UUID userId, UUID agentId, Instant occurredAt) {
}
