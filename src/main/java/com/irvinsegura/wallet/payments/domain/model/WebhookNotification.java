package com.irvinsegura.wallet.payments.domain.model;

import java.util.Objects;

/**
 * A verified provider webhook.
 *
 * @param eventId  the provider's id for the event; providers deliver the same event more than once
 * @param checkout the checkout the event is about; null for event types this application does not act on
 */
public record WebhookNotification(String eventId, String type, CheckoutState checkout) {

    public WebhookNotification {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(type, "type must not be null");
    }
}
