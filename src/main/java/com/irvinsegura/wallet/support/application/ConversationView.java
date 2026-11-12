package com.irvinsegura.wallet.support.application;

import com.irvinsegura.wallet.support.domain.model.SupportConversation;
import com.irvinsegura.wallet.support.domain.model.SupportMessage;
import java.util.List;

/**
 * A conversation with what its reader sees of the people in it.
 *
 * @param userName      who asked; {@code null} for the user themself
 * @param agentName     who attends it, only the first name for the user; {@code null} if nobody
 * @param queuePosition its place in the queue, from 1, while it waits; otherwise {@code null}
 */
public record ConversationView(SupportConversation conversation, String userName, String agentName, Long queuePosition,
        List<SupportMessage> messages) {
}
