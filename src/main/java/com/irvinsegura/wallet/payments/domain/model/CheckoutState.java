package com.irvinsegura.wallet.payments.domain.model;

import com.irvinsegura.wallet.shared.domain.Money;
import java.util.Objects;
import java.util.UUID;

/**
 * What the provider reports about a checkout, from a webhook or when asked directly.
 *
 * @param paymentId         our payment id, sent to the provider when the checkout was opened; null for checkouts
 *                          this application did not open (e.g. test events from the provider's dashboard)
 * @param providerPaymentId the provider's id for the collected money, needed to refund it; set only when paid
 * @param amount            the amount the payer was charged
 */
public record CheckoutState(UUID paymentId, String reference, Status status, String providerPaymentId,
        Money amount) {

    public CheckoutState {
        Objects.requireNonNull(reference, "reference must not be null");
        Objects.requireNonNull(status, "status must not be null");
        if (status == Status.PAID) {
            Objects.requireNonNull(providerPaymentId, "providerPaymentId must not be null when paid");
            Objects.requireNonNull(amount, "amount must not be null when paid");
        }
    }

    public enum Status {
        // Still waiting for the payer
        OPEN,
        PAID,
        // Closed without a payment
        EXPIRED
    }
}
