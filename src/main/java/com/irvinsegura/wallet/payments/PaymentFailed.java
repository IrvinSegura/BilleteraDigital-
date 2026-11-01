package com.irvinsegura.wallet.payments;

import java.time.Instant;
import java.util.UUID;

/**
 * No money was collected and none will be.
 *
 * @param reason one of the constants below
 */
public record PaymentFailed(UUID paymentId, String reason, Instant occurredAt) {

    // The payer did not complete the checkout before it expired
    public static final String EXPIRED = "EXPIRED";
    // The provider could not be reached to open the checkout
    public static final String PROVIDER_UNAVAILABLE = "PROVIDER_UNAVAILABLE";
    // Registered but never started, e.g. the instance stopped in between
    public static final String ABANDONED = "ABANDONED";
}
