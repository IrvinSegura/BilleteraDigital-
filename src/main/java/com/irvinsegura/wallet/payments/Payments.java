package com.irvinsegura.wallet.payments;

import java.util.UUID;

/**
 * Public API of the payments module: collects money from a user through the configured payment provider. The
 * outcome arrives as {@link PaymentSucceeded} or {@link PaymentFailed}, published through the event outbox.
 */
public interface Payments {

    /**
     * Records a payment to be collected, in the caller's transaction, so it is rolled back together with the
     * operation it pays for. Nothing is sent to the provider yet.
     */
    void register(PaymentRequest request);

    /**
     * Opens the payment at the provider. Must be called outside any transaction, after the one that registered the
     * payment committed: it calls the provider over the network.
     *
     * @throws PaymentProviderUnavailableException if the provider could not be reached; the payment is then failed
     */
    PaymentSession start(UUID paymentId);

    /**
     * Gives the money back to the payer, e.g. when it arrived for a wallet that can no longer receive it. Joins the
     * caller's transaction; the provider is called afterwards and retried through the event outbox. Does nothing
     * unless the payment succeeded and was not refunded yet.
     */
    void refund(UUID paymentId);
}
