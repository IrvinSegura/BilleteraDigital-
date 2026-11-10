package com.irvinsegura.wallet.identity;

import com.irvinsegura.wallet.shared.domain.Language;
import java.time.Instant;
import java.util.UUID;

/**
 * Asks for an email with a password reset link. Only published for existing, enabled accounts; the request answers
 * the same either way. Like {@link EmailVerificationRequested}, the token is created when the email is sent.
 *
 * @param language the language of the app that asked; null to use the account's
 */
public record PasswordResetRequested(UUID userId, Language language, Instant occurredAt) {
}
