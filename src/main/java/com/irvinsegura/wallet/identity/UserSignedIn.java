package com.irvinsegura.wallet.identity;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when someone signs in with their password and, if it is on, their second factor. Renewing a session is
 * not signing in.
 */
public record UserSignedIn(UUID userId, Instant occurredAt) {
}
