package com.irvinsegura.wallet.identity;

import com.irvinsegura.wallet.shared.domain.Language;
import java.time.Instant;
import java.util.UUID;

/**
 * Asks for an email with a link to confirm the account's address, at registration or when the user asks again. The
 * link's token is created when the email is sent, never carried in the event, so it is not stored in the outbox.
 *
 * @param language the language of the app that asked; null to use the account's
 */
public record EmailVerificationRequested(UUID userId, Language language, Instant occurredAt) {
}
