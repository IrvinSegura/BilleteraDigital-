package com.irvinsegura.wallet.identity;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when the password changes; every other session has been ended.
 *
 * @param method {@code CHANGE} when the user gave the current password, {@code RESET} when they used an email link
 */
public record PasswordChanged(UUID userId, String method, Instant occurredAt) {
}
