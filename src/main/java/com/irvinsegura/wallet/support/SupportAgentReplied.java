package com.irvinsegura.wallet.support;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when an agent writes to a user, who may have left the chat by then.
 */
public record SupportAgentReplied(UUID conversationId, UUID userId, Instant occurredAt) {
}
