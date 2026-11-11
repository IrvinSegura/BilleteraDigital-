package com.irvinsegura.wallet.support.domain.model;

/**
 * Where a conversation is: with the bot, waiting for an agent, with one, or closed.
 */
public enum ConversationStatus {
    BOT,
    QUEUED,
    ASSIGNED,
    RESOLVED
}
