package com.irvinsegura.wallet.payments.application;

import com.irvinsegura.wallet.payments.PaymentFailed;
import com.irvinsegura.wallet.payments.PaymentProviderUnavailableException;
import com.irvinsegura.wallet.payments.domain.model.CheckoutState;
import com.irvinsegura.wallet.payments.domain.model.Payment;
import com.irvinsegura.wallet.payments.domain.port.PaymentGateway;
import com.irvinsegura.wallet.payments.domain.port.PaymentRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Settles payments a webhook should have settled: webhooks can be lost, delayed past the checkout's expiry, or never
 * sent if the instance stopped between registering a payment and starting it. The provider is asked directly, outside
 * any transaction, and the answer goes through the same transitions as a webhook.
 */
@Service
public class StalePaymentService {

    private static final Logger log = LoggerFactory.getLogger(StalePaymentService.class);
    private static final int BATCH_SIZE = 100;

    private final PaymentRepository repository;
    private final PaymentGateway gateway;
    private final PaymentTransitions transitions;
    private final Clock clock;
    private final Duration abandonAfter;
    private final Duration expiryGrace;

    StalePaymentService(PaymentRepository repository, PaymentGateway gateway, PaymentTransitions transitions,
            Clock clock, @Value("${wallet.payments.stale.abandon-after}") Duration abandonAfter,
            @Value("${wallet.payments.stale.expiry-grace}") Duration expiryGrace) {
        this.repository = repository;
        this.gateway = gateway;
        this.transitions = transitions;
        this.clock = clock;
        this.abandonAfter = abandonAfter;
        this.expiryGrace = expiryGrace;
    }

    /**
     * Returns how many payments changed state.
     */
    public int resolveStalePayments() {
        Instant now = clock.instant();
        int resolved = 0;
        for (UUID paymentId : repository.findCreatedBefore(now.minus(abandonAfter), BATCH_SIZE)) {
            if (transitions.fail(paymentId, PaymentFailed.ABANDONED)) {
                resolved++;
            }
        }
        // The grace period leaves the provider's own expiry webhook time to arrive first
        for (UUID paymentId : repository.findPendingExpiredBefore(now.minus(expiryGrace), BATCH_SIZE)) {
            try {
                if (resolvePending(paymentId)) {
                    resolved++;
                }
            } catch (PaymentProviderUnavailableException e) {
                log.warn("Could not check payment {} with the provider; trying again on the next run", paymentId);
            }
        }
        return resolved;
    }

    private boolean resolvePending(UUID paymentId) {
        Payment payment = repository.findById(paymentId).orElseThrow();
        CheckoutState checkout = gateway.checkCheckout(payment.providerReference());
        if (checkout.status() == CheckoutState.Status.OPEN) {
            checkout = gateway.expireCheckout(payment.providerReference());
        }
        return switch (checkout.status()) {
            case PAID -> transitions.succeed(paymentId, checkout);
            case EXPIRED -> transitions.expire(paymentId);
            case OPEN -> false;
        };
    }
}
