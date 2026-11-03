package com.irvinsegura.wallet.payments.domain.port;

import com.irvinsegura.wallet.payments.domain.model.BalanceTransaction;
import com.irvinsegura.wallet.payments.domain.model.CheckoutSession;
import com.irvinsegura.wallet.payments.domain.model.CheckoutState;
import com.irvinsegura.wallet.payments.domain.model.Payment;
import com.irvinsegura.wallet.payments.domain.model.ProviderBalance;
import com.irvinsegura.wallet.payments.domain.model.WebhookNotification;
import java.time.Instant;
import java.util.List;

/**
 * A payment provider. Every call that changes something at the provider is idempotent per payment, so retrying
 * after a timeout never charges or refunds twice.
 * <p>
 * Implementations throw {@link com.irvinsegura.wallet.payments.PaymentProviderUnavailableException} when the
 * provider cannot be reached or answers with an error.
 */
public interface PaymentGateway {

    /**
     * Stored with each payment, so payments made through an earlier provider are still recognised.
     */
    String name();

    /**
     * Whether collected money stays at the provider until it pays it out (Stripe), rather than landing straight in
     * the partner bank. Only then is the provider's balance followed through {@link #balanceTransactionsSince}.
     */
    boolean holdsFunds();

    CheckoutSession openCheckout(Payment payment);

    CheckoutState checkCheckout(String reference);

    /**
     * Stops a checkout from accepting a payment. Returns its state afterwards: a payer may have paid just before.
     */
    CheckoutState expireCheckout(String reference);

    /**
     * Gives the whole amount back. Returns the provider's id for the refund.
     */
    String refund(Payment payment);

    /**
     * Verifies a webhook really comes from the provider and reads it.
     *
     * @throws com.irvinsegura.wallet.payments.domain.model.InvalidWebhookException if it cannot be verified or read
     */
    WebhookNotification readWebhook(String payload, String signature);

    /**
     * Every movement of the provider's balance created at or after {@code since}, oldest first.
     */
    List<BalanceTransaction> balanceTransactionsSince(Instant since);

    /**
     * What the provider holds now, per currency.
     */
    List<ProviderBalance> balance();
}
