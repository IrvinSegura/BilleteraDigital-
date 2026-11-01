package com.irvinsegura.wallet.payments.application;

import com.irvinsegura.wallet.payments.domain.model.CheckoutState;
import com.irvinsegura.wallet.payments.domain.model.WebhookNotification;
import com.irvinsegura.wallet.payments.domain.port.PaymentGateway;
import com.irvinsegura.wallet.payments.domain.port.WebhookEventRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies provider webhooks. The event id is recorded in the same transaction as its effect: a redelivery after
 * success is skipped, and a failure rolls both back so the provider's own retry processes it again.
 */
@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    private final PaymentGateway gateway;
    private final WebhookEventRepository webhookEvents;
    private final PaymentTransitions transitions;
    private final Clock clock;

    WebhookService(PaymentGateway gateway, WebhookEventRepository webhookEvents, PaymentTransitions transitions,
            Clock clock) {
        this.gateway = gateway;
        this.webhookEvents = webhookEvents;
        this.transitions = transitions;
        this.clock = clock;
    }

    /**
     * @throws com.irvinsegura.wallet.payments.domain.model.InvalidWebhookException if the webhook is not genuine
     */
    @Transactional
    public void handle(String payload, String signature) {
        WebhookNotification notification = gateway.readWebhook(payload, signature);
        if (!webhookEvents.recordIfNew(gateway.name(), notification.eventId(), notification.type(), clock.instant())) {
            log.debug("Skipping webhook event {}: already processed", notification.eventId());
            return;
        }
        CheckoutState checkout = notification.checkout();
        if (checkout == null) {
            return;
        }
        switch (checkout.status()) {
            case PAID -> transitions.succeed(checkout.paymentId(), checkout);
            case EXPIRED -> transitions.expire(checkout.paymentId());
            case OPEN -> log.debug("Checkout {} is still open", checkout.reference());
        }
    }
}
