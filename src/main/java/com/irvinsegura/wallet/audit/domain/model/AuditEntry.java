package com.irvinsegura.wallet.audit.domain.model;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One line of the audit log: who did what to which resource, and when.
 *
 * @param actorId who caused it; null for actions taken by the system itself (e.g. a lockout)
 */
public record AuditEntry(UUID id, Instant occurredAt, UUID actorId, String action, String targetType,
        UUID targetId, String details) {

    private static final int MAX_DETAILS_LENGTH = 500;

    public AuditEntry {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(targetType, "targetType must not be null");
        Objects.requireNonNull(targetId, "targetId must not be null");
        if (details != null && details.length() > MAX_DETAILS_LENGTH) {
            details = details.substring(0, MAX_DETAILS_LENGTH);
        }
    }

    /**
     * The id is derived from what happened rather than random, so recording the same event twice (an event can be
     * redelivered after a crash) produces the same entry instead of a duplicate.
     *
     * @param eventKey identifies the event uniquely, e.g. the deposit id or wallet id plus timestamp
     */
    public static AuditEntry of(String action, String eventKey, Instant occurredAt, UUID actorId, String targetType,
            UUID targetId, String details) {
        UUID id = UUID.nameUUIDFromBytes((action + ":" + eventKey).getBytes(StandardCharsets.UTF_8));
        return new AuditEntry(id, occurredAt, actorId, action, targetType, targetId, details);
    }
}
