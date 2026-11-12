package com.irvinsegura.wallet.support;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when an agent takes over a conversation the bot could not settle.
 */
public record SupportCaseAssigned(UUID conversationId, UUID userId, UUID agentId, Instant occurredAt) {
}
