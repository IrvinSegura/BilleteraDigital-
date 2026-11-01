package com.irvinsegura.wallet.payments.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * A checkout just opened at the provider.
 *
 * @param reference         the provider's id for it, e.g. a Stripe Checkout Session id
 * @param url               where the payer completes it; null when it was paid on creation
 * @param expiresAt         when the provider stops accepting it; null when it was paid on creation
 * @param capturedPaymentId the provider's id for the collected money when it was paid on creation (the simulated
 *                          provider); null otherwise
 */
public record CheckoutSession(String reference, String url, Instant expiresAt, String capturedPaymentId) {

    public CheckoutSession {
        Objects.requireNonNull(reference, "reference must not be null");
    }

    public boolean isPaid() {
        return capturedPaymentId != null;
    }
}
