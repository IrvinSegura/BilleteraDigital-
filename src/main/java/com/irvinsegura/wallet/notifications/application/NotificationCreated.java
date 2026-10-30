package com.irvinsegura.wallet.notifications.application;

import java.util.UUID;

/**
 * Internal to the module: asks for the email copy of a stored notification. Going through the outbox keeps the inbox
 * and the email independent, so a mail server outage delays only the email, which is retried until it is sent.
 */
public record NotificationCreated(UUID notificationId, UUID userId) {
}
