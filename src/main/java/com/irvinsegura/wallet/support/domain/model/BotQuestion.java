package com.irvinsegura.wallet.support.domain.model;

import com.irvinsegura.wallet.shared.domain.Language;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * What the bot made of a message a user wrote.
 *
 * @param messageId the user's message, which holds what was asked
 * @param language  which knowledge files it was answered from
 * @param topic     the section it answered with or, when it offered some, their titles one per line; {@code null}
 *                  otherwise
 */
public record BotQuestion(UUID messageId, UUID conversationId, Language language, BotOutcome outcome, String topic,
        Instant createdAt) {

    private static final String TOPIC_SEPARATOR = "\n";

    public BotQuestion {
        Objects.requireNonNull(messageId, "messageId must not be null");
        Objects.requireNonNull(conversationId, "conversationId must not be null");
        Objects.requireNonNull(language, "language must not be null");
        Objects.requireNonNull(outcome, "outcome must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public static BotQuestion of(SupportMessage question, BotReply reply, Language language) {
        String topic = reply.topic() != null ? reply.topic()
                : reply.suggestions().isEmpty() ? null : String.join(TOPIC_SEPARATOR, reply.suggestions());
        return new BotQuestion(question.id(), question.conversationId(), language, reply.outcome(), topic,
                question.createdAt());
    }
}
