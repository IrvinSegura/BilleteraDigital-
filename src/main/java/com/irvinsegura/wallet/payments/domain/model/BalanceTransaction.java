package com.irvinsegura.wallet.payments.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * One movement of the payment provider's balance, as the provider reports it.
 *
 * @param providerType      the provider's own name for it, kept for people looking at what could not be matched
 * @param amountMinor       signed: negative when money left the provider's balance (payouts, refunds, fees)
 * @param feeMinor          what the provider kept from this movement, e.g. its fee on a charge
 * @param providerPaymentId the collected payment a charge or refund belongs to (a Stripe PaymentIntent); null for
 *                          other types
 */
public record BalanceTransaction(String id, Type type, String providerType, long amountMinor, long feeMinor,
        String currency, String providerPaymentId, Instant createdAt) {

    public BalanceTransaction {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(providerType, "providerType must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        if (feeMinor < 0) {
            throw new IllegalArgumentException("feeMinor cannot be negative: " + feeMinor);
        }
    }

    public enum Type {
        // A payer's money arrived; only its fee is new to the ledger, the money itself was recorded on collection
        CHARGE,
        REFUND,
        // The provider sent money to the partner bank
        PAYOUT,
        // A payout failed or was cancelled, so its money is back at the provider
        PAYOUT_REVERSAL,
        // A fee charged on its own, e.g. for the account
        FEE,
        // Anything else (disputes, adjustments...): left for a person
        OTHER
    }
}
