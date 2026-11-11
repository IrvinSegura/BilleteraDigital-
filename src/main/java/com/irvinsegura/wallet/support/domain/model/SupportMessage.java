package com.irvinsegura.wallet.support.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A line of the chat. What happened to the conversation is recorded as a {@code SYSTEM} message with an {@code event}
 * and no text, so each app says it in its reader's language.
 *
 * @param senderId     who wrote it; {@code null} for the bot and the system
 * @param asksFeedback an answer of the bot the user can say helped or not
 * @param attachment   what came with it, without its bytes; {@code null} if nothing
 * @param suggestions  questions the bot offers to pick from, when it understood only part of a message
 */
public record SupportMessage(UUID id, UUID conversationId, MessageSender sender, UUID senderId, String text,
        ConversationEvent event, boolean asksFeedback, SupportAttachment attachment, List<String> suggestions,
        Instant createdAt) {

    public SupportMessage {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(conversationId, "conversationId must not be null");
        Objects.requireNonNull(sender, "sender must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        suggestions = List.copyOf(suggestions);
    }

    public static SupportMessage fromPerson(UUID conversationId, MessageSender sender, UUID senderId, String text,
            SupportAttachment attachment, Instant at) {
        return new SupportMessage(UUID.randomUUID(), conversationId, sender, senderId,
                text == null || text.isBlank() ? null : text.strip(), null, false, attachment, List.of(), at);
    }

    public static SupportMessage fromBot(UUID conversationId, BotReply reply, Instant at) {
        return new SupportMessage(UUID.randomUUID(), conversationId, MessageSender.BOT, null, reply.text(), null,
                reply.asksFeedback(), null, reply.suggestions(), at);
    }

    public static SupportMessage event(UUID conversationId, ConversationEvent event, Instant at) {
        return new SupportMessage(UUID.randomUUID(), conversationId, MessageSender.SYSTEM, null, null, event, false,
                null, List.of(), at);
    }
}
