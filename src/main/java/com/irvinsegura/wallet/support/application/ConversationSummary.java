package com.irvinsegura.wallet.support.application;

import com.irvinsegura.wallet.support.domain.model.SupportConversation;
import java.time.Instant;

/**
 * A row in a list of cases.
 *
 * @param lastText the last thing written in it
 */
public record ConversationSummary(SupportConversation conversation, String userName, String agentName, String lastText,
        Instant lastMessageAt) {
}
