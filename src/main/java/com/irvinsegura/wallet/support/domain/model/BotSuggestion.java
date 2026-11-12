package com.irvinsegura.wallet.support.domain.model;

import com.irvinsegura.wallet.shared.domain.Language;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A question an agent answered in a chat and thinks the bot could answer by itself. The bot's knowledge lives in
 * files kept with the code, so an administrator reads it, adds it to them and marks it added, or dismisses it.
 */
public class BotSuggestion {

    public static final int MAX_QUESTION = 300;
    public static final int MAX_ANSWER = 2000;

    public enum Status { OPEN, ADDED, DISMISSED }

    private final UUID id;
    private final UUID conversationId;
    private final UUID agentId;
    private final Language language;
    private final String question;
    private final String answer;
    private final Instant createdAt;
    private Status status;
    private UUID reviewedBy;
    private Instant reviewedAt;

    private BotSuggestion(UUID id, UUID conversationId, UUID agentId, Language language, String question,
            String answer, Instant createdAt, Status status, UUID reviewedBy, Instant reviewedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.conversationId = conversationId;
        this.agentId = Objects.requireNonNull(agentId, "agentId must not be null");
        this.language = Objects.requireNonNull(language, "language must not be null");
        this.question = required(question, MAX_QUESTION, "question");
        this.answer = required(answer, MAX_ANSWER, "answer");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.reviewedBy = reviewedBy;
        this.reviewedAt = reviewedAt;
    }

    public static BotSuggestion propose(UUID conversationId, UUID agentId, Language language, String question,
            String answer, Instant at) {
        return new BotSuggestion(UUID.randomUUID(), conversationId, agentId, language, question, answer, at,
                Status.OPEN, null, null);
    }

    public static BotSuggestion restore(UUID id, UUID conversationId, UUID agentId, Language language,
            String question, String answer, Instant createdAt, Status status, UUID reviewedBy, Instant reviewedAt) {
        return new BotSuggestion(id, conversationId, agentId, language, question, answer, createdAt, status,
                reviewedBy, reviewedAt);
    }

    /**
     * @param outcome {@link Status#ADDED} or {@link Status#DISMISSED}
     * @throws BotSuggestionReviewedException if someone already reviewed it
     */
    public void review(Status outcome, UUID administratorId, Instant at) {
        if (outcome == Status.OPEN) {
            throw new IllegalArgumentException("A review adds or dismisses a suggestion");
        }
        if (status != Status.OPEN) {
            throw new BotSuggestionReviewedException();
        }
        status = outcome;
        reviewedBy = Objects.requireNonNull(administratorId, "administratorId must not be null");
        reviewedAt = Objects.requireNonNull(at, "at must not be null");
    }

    private static String required(String text, int max, String what) {
        String stripped = text == null ? "" : text.strip();
        if (stripped.isEmpty() || stripped.length() > max) {
            throw new IllegalArgumentException("A suggestion needs a " + what + " of up to " + max + " characters");
        }
        return stripped;
    }

    public UUID id() {
        return id;
    }

    public UUID conversationId() {
        return conversationId;
    }

    public UUID agentId() {
        return agentId;
    }

    public Language language() {
        return language;
    }

    public String question() {
        return question;
    }

    public String answer() {
        return answer;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Status status() {
        return status;
    }

    public UUID reviewedBy() {
        return reviewedBy;
    }

    public Instant reviewedAt() {
        return reviewedAt;
    }
}
