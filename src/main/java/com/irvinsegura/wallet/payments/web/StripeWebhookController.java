package com.irvinsegura.wallet.payments.web;

import com.irvinsegura.wallet.payments.application.WebhookService;
import io.swagger.v3.oas.annotations.Hidden;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Called by Stripe, not by users: no access token, the signature authenticates the request. The body is taken as
 * raw bytes because the signature covers them exactly. Only exists when Stripe is the configured provider.
 */
@Hidden
@RestController
@ConditionalOnProperty(name = "wallet.payments.provider", havingValue = "stripe")
class StripeWebhookController {

    private final WebhookService webhookService;

    StripeWebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    // Any 2xx tells Stripe to stop redelivering; an error makes it retry with backoff for up to three days
    @PostMapping("/api/v1/payments/webhooks/stripe")
    ResponseEntity<Void> receive(@RequestBody byte[] payload,
            @RequestHeader(name = "Stripe-Signature", required = false) String signature) {
        webhookService.handle(new String(payload, StandardCharsets.UTF_8), signature);
        return ResponseEntity.ok().build();
    }
}
