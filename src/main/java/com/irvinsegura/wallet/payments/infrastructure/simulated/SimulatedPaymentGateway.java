package com.irvinsegura.wallet.payments.infrastructure.simulated;

import com.irvinsegura.wallet.payments.domain.model.BalanceTransaction;
import com.irvinsegura.wallet.payments.domain.model.CheckoutSession;
import com.irvinsegura.wallet.payments.domain.model.CheckoutState;
import com.irvinsegura.wallet.payments.domain.model.InvalidWebhookException;
import com.irvinsegura.wallet.payments.domain.model.Payment;
import com.irvinsegura.wallet.payments.domain.model.ProviderBalance;
import com.irvinsegura.wallet.payments.domain.model.WebhookNotification;
import com.irvinsegura.wallet.payments.domain.port.PaymentGateway;
import java.time.Instant;
import java.util.List;

/**
 * For development and tests without a provider account: every checkout is paid the moment it is opened, so a
 * deposit is credited within the request that makes it.
 */
public class SimulatedPaymentGateway implements PaymentGateway {

    public static final String NAME = "SIMULATED";

    @Override
    public String name() {
        return NAME;
    }

    // Its money counts as already in the partner bank, like a bank transfer received directly
    @Override
    public boolean holdsFunds() {
        return false;
    }

    @Override
    public CheckoutSession openCheckout(Payment payment) {
        return new CheckoutSession("int_cs_" + payment.id(), null, null, "int_pi_" + payment.id());
    }

    // Simulated checkouts are paid when opened, so none is ever left waiting to be checked or expired
    @Override
    public CheckoutState checkCheckout(String reference) {
        throw new IllegalStateException("Simulated checkout " + reference + " cannot be pending");
    }

    @Override
    public CheckoutState expireCheckout(String reference) {
        throw new IllegalStateException("Simulated checkout " + reference + " cannot be pending");
    }

    @Override
    public String refund(Payment payment) {
        return "int_re_" + payment.id();
    }

    @Override
    public WebhookNotification readWebhook(String payload, String signature) {
        throw new InvalidWebhookException("The simulated provider sends no webhooks");
    }

    @Override
    public List<BalanceTransaction> balanceTransactionsSince(Instant since) {
        return List.of();
    }

    @Override
    public List<ProviderBalance> balance() {
        return List.of();
    }
}
