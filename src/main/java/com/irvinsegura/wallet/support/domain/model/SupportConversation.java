package com.irvinsegura.wallet.support.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One user's chat with support: the bot answers it first, and what the bot can't settle waits in the queue until an
 * agent with no other case takes it.
 */
public class SupportConversation {

    public static final int MIN_SCORE = 1;
    public static final int MAX_SCORE = 5;

    private final UUID id;
    private final UUID userId;
    private final Instant createdAt;
    private ConversationStatus status;
    private UUID agentId;
    private Instant escalatedAt;
    private Instant assignedAt;
    private Instant resolvedAt;
    private Integer surveyScore;
    private String surveyComment;
    private Instant surveyedAt;
    private CasePriority priority;
    private CaseResolution resolution;

    private SupportConversation(UUID id, UUID userId, Instant createdAt, ConversationStatus status, UUID agentId,
            Instant escalatedAt, Instant assignedAt, Instant resolvedAt, Integer surveyScore, String surveyComment,
            Instant surveyedAt, CasePriority priority, CaseResolution resolution) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.agentId = agentId;
        this.escalatedAt = escalatedAt;
        this.assignedAt = assignedAt;
        this.resolvedAt = resolvedAt;
        this.surveyScore = surveyScore;
        this.surveyComment = surveyComment;
        this.surveyedAt = surveyedAt;
        this.priority = Objects.requireNonNull(priority, "priority must not be null");
        this.resolution = resolution;
    }

    public static SupportConversation start(UUID userId, Instant at) {
        return new SupportConversation(UUID.randomUUID(), userId, at, ConversationStatus.BOT, null, null, null, null,
                null, null, null, CasePriority.NORMAL, null);
    }

    public static SupportConversation restore(UUID id, UUID userId, Instant createdAt, ConversationStatus status,
            UUID agentId, Instant escalatedAt, Instant assignedAt, Instant resolvedAt, Integer surveyScore,
            String surveyComment, Instant surveyedAt, CasePriority priority, CaseResolution resolution) {
        return new SupportConversation(id, userId, createdAt, status, agentId, escalatedAt, assignedAt, resolvedAt,
                surveyScore, surveyComment, surveyedAt, priority, resolution);
    }

    /**
     * Passes it from the bot to the queue, where cases of a higher {@code priority} go first. Returns whether
     * anything changed: one already with people stays as it is.
     */
    public boolean escalate(Instant at, CasePriority priority) {
        if (status != ConversationStatus.BOT) {
            return false;
        }
        status = ConversationStatus.QUEUED;
        escalatedAt = at;
        this.priority = Objects.requireNonNull(priority, "priority must not be null");
        return true;
    }

    public void assignTo(UUID agent, Instant at) {
        if (status != ConversationStatus.QUEUED) {
            throw new IllegalStateException("Only a conversation in the queue can be assigned, this one is " + status);
        }
        status = ConversationStatus.ASSIGNED;
        agentId = Objects.requireNonNull(agent, "agent must not be null");
        assignedAt = at;
    }

    /**
     * Passes the case to another agent, who attends it from now on.
     */
    public void transferTo(UUID agent, Instant at) {
        if (status != ConversationStatus.ASSIGNED) {
            throw new IllegalStateException("Only an assigned conversation can be passed on, this one is " + status);
        }
        agentId = Objects.requireNonNull(agent, "agent must not be null");
        assignedAt = at;
    }

    /**
     * Back to the queue, keeping its place, because its agent can no longer attend it.
     */
    public void release() {
        if (status != ConversationStatus.ASSIGNED) {
            throw new IllegalStateException("Only an assigned conversation can be released, this one is " + status);
        }
        status = ConversationStatus.QUEUED;
        agentId = null;
        assignedAt = null;
    }

    /**
     * @param resolution how it ended; {@code null} for one only the bot attended
     * @throws ConversationClosedException if it was already closed
     */
    public void resolve(Instant at, CaseResolution resolution) {
        requireOpen();
        status = ConversationStatus.RESOLVED;
        resolvedAt = at;
        this.resolution = resolution;
    }

    /**
     * @throws SurveyNotAvailableException if no agent attended it, it is still open, or it was already rated
     */
    public void answerSurvey(int score, String comment, Instant at) {
        if (!awaitsSurvey()) {
            throw new SurveyNotAvailableException();
        }
        if (score < MIN_SCORE || score > MAX_SCORE) {
            throw new IllegalArgumentException("Score out of range: " + score);
        }
        surveyScore = score;
        surveyComment = comment == null || comment.isBlank() ? null : comment.strip();
        surveyedAt = at;
    }

    /**
     * @throws ConversationClosedException if it was already closed
     */
    public void requireOpen() {
        if (status == ConversationStatus.RESOLVED) {
            throw new ConversationClosedException();
        }
    }

    public boolean awaitsSurvey() {
        return status == ConversationStatus.RESOLVED && agentId != null && surveyScore == null;
    }

    public UUID id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public ConversationStatus status() {
        return status;
    }

    public UUID agentId() {
        return agentId;
    }

    public Instant escalatedAt() {
        return escalatedAt;
    }

    public Instant assignedAt() {
        return assignedAt;
    }

    public Instant resolvedAt() {
        return resolvedAt;
    }

    public Integer surveyScore() {
        return surveyScore;
    }

    public String surveyComment() {
        return surveyComment;
    }

    public Instant surveyedAt() {
        return surveyedAt;
    }

    public CasePriority priority() {
        return priority;
    }

    public CaseResolution resolution() {
        return resolution;
    }
}
