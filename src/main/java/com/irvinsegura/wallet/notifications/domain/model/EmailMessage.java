package com.irvinsegura.wallet.notifications.domain.model;

import java.util.Objects;

/**
 * A plain-text email. It holds the recipient's address, so it is built right before sending and never stored or
 * logged.
 */
public record EmailMessage(String to, String subject, String body) {

    public EmailMessage {
        Objects.requireNonNull(to, "to must not be null");
        Objects.requireNonNull(subject, "subject must not be null");
        Objects.requireNonNull(body, "body must not be null");
    }

    public static EmailMessage of(Notification notification, String email, String fullName) {
        return new EmailMessage(email, notification.title(), """
                Hi %s,

                %s

                You can see all your notifications in the Digital Wallet app.
                """.formatted(fullName, notification.body()));
    }
}
