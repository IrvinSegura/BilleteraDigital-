package com.irvinsegura.wallet.support.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An answer an agent keeps to send again, such as how long an INE review takes. Each agent keeps their own.
 */
public record SavedReply(UUID id, UUID agentId, String title, String text, Instant createdAt) {

    public static final int MAX_TITLE = 60;
    public static final int MAX_TEXT = 2000;
    public static final int MAX_PER_AGENT = 50;

    public SavedReply {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(agentId, "agentId must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        title = title == null ? "" : title.strip();
        text = text == null ? "" : text.strip();
        if (title.isEmpty() || title.length() > MAX_TITLE) {
            throw new IllegalArgumentException("A saved reply needs a title of up to " + MAX_TITLE + " characters");
        }
        if (text.isEmpty() || text.length() > MAX_TEXT) {
            throw new IllegalArgumentException("A saved reply needs a text of up to " + MAX_TEXT + " characters");
        }
    }

    public static SavedReply create(UUID agentId, String title, String text, Instant at) {
        return new SavedReply(UUID.randomUUID(), agentId, title, text, at);
    }
}
