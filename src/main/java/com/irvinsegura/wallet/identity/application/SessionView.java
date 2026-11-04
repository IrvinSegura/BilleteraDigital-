package com.irvinsegura.wallet.identity.application;

import java.time.Instant;
import java.util.UUID;

/**
 * One of the user's signed-in sessions.
 *
 * @param lastUsedAt when its token was last refreshed (or the session started)
 * @param current    whether it is the session making the request
 */
public record SessionView(UUID id, String userAgent, String ipAddress, Instant startedAt, Instant lastUsedAt,
        Instant expiresAt, boolean current) {
}
