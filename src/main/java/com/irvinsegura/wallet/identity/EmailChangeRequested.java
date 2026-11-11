package com.irvinsegura.wallet.identity;

import com.irvinsegura.wallet.shared.domain.Language;
import java.time.Instant;
import java.util.UUID;

/**
 * Asks for an email to {@code newEmail} with a link that moves the account to it. Like the verification email, the
 * link's token is created when the email is sent and never travels in the event.
 *
 * @param language the language of the app that asked; null to use the account's
 */
public record EmailChangeRequested(UUID userId, String newEmail, Language language, Instant occurredAt) {
}
