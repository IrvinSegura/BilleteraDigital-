package com.irvinsegura.wallet.notifications.domain.model;

import java.util.Objects;

/**
 * An email with its plain-text and HTML forms. It holds the recipient's address, so it is built right before sending
 * and never stored or logged.
 *
 * @param body the plain-text form, for clients that don't show HTML
 * @param html the designed form
 */
public record EmailMessage(String to, String subject, String body, String html) {

    public EmailMessage {
        Objects.requireNonNull(to, "to must not be null");
        Objects.requireNonNull(subject, "subject must not be null");
        Objects.requireNonNull(body, "body must not be null");
        Objects.requireNonNull(html, "html must not be null");
    }
}
