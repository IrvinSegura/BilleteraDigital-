package com.irvinsegura.wallet.payments;

import java.time.Instant;
import java.util.UUID;

/**
 * The provider confirmed the money was collected. The amount was already checked against the registered payment.
 */
public record PaymentSucceeded(UUID paymentId, long amountMinor, String currency, Instant occurredAt) {
}
