package com.irvinsegura.wallet.notifications.domain.model;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A message for one user. The text is written when the notification is created, so it keeps saying what happened
 * even if names or wallets change later.
 *
 * @param referenceId what the notification is about (deposit, withdrawal, transfer or wallet id); null when nothing
 * @param readAt      null while unread
 * @param params      the figures behind the text, for writing its email in the recipient's language: amounts, names...
 */
public record Notification(UUID id, UUID userId, NotificationType type, String title, String body, UUID referenceId,
        Instant createdAt, Instant readAt, Map<String, String> params) {

    public static final int MAX_TITLE_LENGTH = 100;
    public static final int MAX_BODY_LENGTH = 500;

    public Notification {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        requireLength(title, MAX_TITLE_LENGTH, "title");
        requireLength(body, MAX_BODY_LENGTH, "body");
        params = Map.copyOf(params);
    }

    /**
     * Creates an unread notification. The id is derived from the event and the recipient rather than random, so
     * handling the same event twice (events can be redelivered after a crash) produces the same notification.
     *
     * @param eventKey identifies the event uniquely, e.g. the transfer id, or the wallet id plus timestamp
     */
    public static Notification create(NotificationType type, String eventKey, UUID userId, String title, String body,
            UUID referenceId, Instant createdAt, Map<String, String> params) {
        UUID id = UUID.nameUUIDFromBytes((type + ":" + eventKey + ":" + userId).getBytes(StandardCharsets.UTF_8));
        return new Notification(id, userId, type, title, body, referenceId, createdAt, null, params);
    }

    /**
     * A notification whose text has no figures in it.
     */
    public static Notification create(NotificationType type, String eventKey, UUID userId, String title, String body,
            UUID referenceId, Instant createdAt) {
        return create(type, eventKey, userId, title, body, referenceId, createdAt, Map.of());
    }

    public boolean isRead() {
        return readAt != null;
    }

    private static void requireLength(String value, int maxLength, String name) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException("%s must have between 1 and %d characters".formatted(name, maxLength));
        }
    }
}
