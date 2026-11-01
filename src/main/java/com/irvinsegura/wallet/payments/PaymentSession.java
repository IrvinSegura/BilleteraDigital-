package com.irvinsegura.wallet.payments;

import java.time.Instant;
import java.util.UUID;

/**
 * Result of starting a payment.
 *
 * @param succeeded   whether the money was already collected (the simulated provider); otherwise the payer must
 *                    complete the checkout and the result arrives later as an event
 * @param checkoutUrl provider page where the payer completes the payment; null when it already succeeded
 * @param expiresAt   after this the checkout no longer accepts the payment; null when it already succeeded
 */
public record PaymentSession(UUID paymentId, boolean succeeded, String checkoutUrl, Instant expiresAt) {
}
