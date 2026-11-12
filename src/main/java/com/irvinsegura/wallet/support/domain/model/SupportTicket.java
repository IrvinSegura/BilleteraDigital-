package com.irvinsegura.wallet.support.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A problem that support can't fix: the agent attending the customer raises it, tied to the operation it is about or,
 * for a problem with the account itself, to a {@link TicketCategory}, and an administrator settles it with the
 * operations tools. Agents never move money nor change accounts.
 */
public class SupportTicket {

    public static final int MAX_DESCRIPTION = 1000;
    public static final int MAX_RESOLUTION = 500;

    private final UUID id;
    private final UUID conversationId;
    private final UUID userId;
    private final UUID openedBy;
    private final TicketCategory category;
    private final String operationKind;
    private final UUID operationId;
    private final String description;
    private final Instant createdAt;
    private TicketStatus status;
    private String resolution;
    private UUID resolvedBy;
    private Instant resolvedAt;

    private SupportTicket(UUID id, UUID conversationId, UUID userId, UUID openedBy, TicketCategory category,
            String operationKind, UUID operationId, String description, Instant createdAt, TicketStatus status, String resolution,
            UUID resolvedBy, Instant resolvedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.conversationId = Objects.requireNonNull(conversationId, "conversationId must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.openedBy = Objects.requireNonNull(openedBy, "openedBy must not be null");
        this.category = Objects.requireNonNull(category, "category must not be null");
        if ((category == TicketCategory.OPERATION) != (operationId != null)
                || (operationId == null) != (operationKind == null)) {
            throw new IllegalArgumentException("Only a ticket about an operation points at one, and with its kind");
        }
        this.operationKind = operationKind;
        this.operationId = operationId;
        this.description = Objects.requireNonNull(description, "description must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.resolution = resolution;
        this.resolvedBy = resolvedBy;
        this.resolvedAt = resolvedAt;
    }

    /**
     * @param operationKind {@code null} unless the category is {@link TicketCategory#OPERATION}, as is
     *                      {@code operationId}
     * @param description   what the agent learned from the customer, for whoever settles it
     */
    public static SupportTicket open(UUID conversationId, UUID userId, UUID agentId, TicketCategory category,
            String operationKind, UUID operationId, String description, Instant at) {
        String text = description == null ? "" : description.strip();
        if (text.isEmpty() || text.length() > MAX_DESCRIPTION) {
            throw new IllegalArgumentException("A ticket needs a description of up to " + MAX_DESCRIPTION + " characters");
        }
        return new SupportTicket(UUID.randomUUID(), conversationId, userId, agentId, category, operationKind,
                operationId, text, at, TicketStatus.OPEN, null, null, null);
    }

    public static SupportTicket restore(UUID id, UUID conversationId, UUID userId, UUID openedBy,
            TicketCategory category, String operationKind, UUID operationId, String description, Instant createdAt,
            TicketStatus status, String resolution, UUID resolvedBy, Instant resolvedAt) {
        return new SupportTicket(id, conversationId, userId, openedBy, category, operationKind, operationId,
                description, createdAt, status, resolution, resolvedBy, resolvedAt);
    }

    /**
     * Closes it with what was done, which the customer is told.
     *
     * @throws TicketClosedException if it was already settled
     */
    public void resolve(UUID adminId, String text, Instant at) {
        if (status != TicketStatus.OPEN) {
            throw new TicketClosedException();
        }
        String note = text == null ? "" : text.strip();
        if (note.isEmpty() || note.length() > MAX_RESOLUTION) {
            throw new IllegalArgumentException("A resolution needs up to " + MAX_RESOLUTION + " characters");
        }
        status = TicketStatus.RESOLVED;
        resolution = note;
        resolvedBy = Objects.requireNonNull(adminId, "adminId must not be null");
        resolvedAt = at;
    }

    public UUID id() {
        return id;
    }

    public UUID conversationId() {
        return conversationId;
    }

    public UUID userId() {
        return userId;
    }

    public UUID openedBy() {
        return openedBy;
    }

    public TicketCategory category() {
        return category;
    }

    /**
     * @return {@code null} unless it is about an operation
     */
    public String operationKind() {
        return operationKind;
    }

    public UUID operationId() {
        return operationId;
    }

    public String description() {
        return description;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public TicketStatus status() {
        return status;
    }

    public String resolution() {
        return resolution;
    }

    public UUID resolvedBy() {
        return resolvedBy;
    }

    public Instant resolvedAt() {
        return resolvedAt;
    }
}
