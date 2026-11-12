package com.irvinsegura.wallet.support.domain.model;

import java.util.UUID;

/**
 * A file sent in a chat, without its bytes, which are only read when someone downloads it.
 *
 * @param contentType worked out from the file's bytes, never taken from what the client says it is
 */
public record SupportAttachment(UUID id, UUID conversationId, String fileName, String contentType, long sizeBytes) {
}
