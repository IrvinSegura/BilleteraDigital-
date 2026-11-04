package com.irvinsegura.wallet.identity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Published when a user changes their own profile. Carries which fields changed, never their values.
 *
 * @param fields {@code FULL_NAME}, {@code PHONE}
 */
public record ProfileUpdated(UUID userId, List<String> fields, Instant occurredAt) {
}
