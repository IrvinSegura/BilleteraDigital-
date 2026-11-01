package com.irvinsegura.wallet.payments.application;

import java.time.Instant;
import java.util.UUID;

/**
 * Internal to the payments module: stored in the event outbox with the refund request, so the provider is called
 * after the commit and again until it answers.
 */
public record RefundRequested(UUID paymentId, Instant occurredAt) {
}
