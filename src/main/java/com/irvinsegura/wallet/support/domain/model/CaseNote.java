package com.irvinsegura.wallet.support.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * What the team writes on a case for each other, such as what was already checked. The customer never reads it; it
 * stays with the case when it passes to a colleague, and administrators read it with the case.
 */
public record CaseNote(UUID id, UUID conversationId, UUID authorId, String text, Instant createdAt) {

    public static final int MAX_TEXT = 1000;

    public CaseNote {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(conversationId, "conversationId must not be null");
        Objects.requireNonNull(authorId, "authorId must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        text = text == null ? "" : text.strip();
        if (text.isEmpty() || text.length() > MAX_TEXT) {
            throw new IllegalArgumentException("A note needs a text of up to " + MAX_TEXT + " characters");
        }
    }

    public static CaseNote write(UUID conversationId, UUID authorId, String text, Instant at) {
        return new CaseNote(UUID.randomUUID(), conversationId, authorId, text, at);
    }
}
