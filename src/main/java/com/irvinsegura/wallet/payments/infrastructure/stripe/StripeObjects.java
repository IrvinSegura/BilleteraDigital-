package com.irvinsegura.wallet.payments.infrastructure.stripe;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * The few fields read from Stripe's JSON. Unknown fields are ignored, so new API versions adding fields break
 * nothing.
 */
final class StripeObjects {

    private StripeObjects() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Event(String id, String type, EventData data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EventData(CheckoutSessionObject object) {
    }

    /**
     * @param status        open, complete or expired
     * @param paymentStatus paid, unpaid or no_payment_required
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record CheckoutSessionObject(
            String id,
            String url,
            String status,
            @JsonProperty("payment_status") String paymentStatus,
            @JsonProperty("payment_intent") String paymentIntent,
            @JsonProperty("client_reference_id") String clientReferenceId,
            @JsonProperty("amount_total") Long amountTotal,
            String currency,
            @JsonProperty("expires_at") Long expiresAt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Refund(String id) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record BalanceTransactionList(List<BalanceTransactionObject> data, @JsonProperty("has_more") boolean hasMore) {
    }

    /**
     * @param source the expanded object behind it (a charge, refund or payout) read as a map, since it may also be
     *               a plain id
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record BalanceTransactionObject(String id, String type, long amount, long fee, String currency, long created,
            Object source) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Balance(List<BalanceAmount> available, List<BalanceAmount> pending) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record BalanceAmount(long amount, String currency) {
    }

    // Only the envelope, read first so events of other types are never parsed as a checkout session
    @JsonIgnoreProperties(ignoreUnknown = true)
    record EventEnvelope(String id, String type) {
    }
}
