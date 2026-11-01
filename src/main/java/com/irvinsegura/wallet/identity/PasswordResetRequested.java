package com.irvinsegura.wallet.identity;

import java.time.Instant;
import java.util.UUID;

/**
 * Asks for an email with a password reset link. Only published for existing, enabled accounts; the request answers
 * the same either way. Like {@link EmailVerificationRequested}, the token is created when the email is sent.
 */
public record PasswordResetRequested(UUID userId, Instant occurredAt) {
}
