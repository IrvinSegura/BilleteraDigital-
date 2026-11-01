package com.irvinsegura.wallet.payments.application;

import com.irvinsegura.wallet.payments.PaymentFailed;
import com.irvinsegura.wallet.payments.PaymentSession;
import com.irvinsegura.wallet.payments.PaymentSucceeded;
import com.irvinsegura.wallet.payments.domain.model.CheckoutSession;
import com.irvinsegura.wallet.payments.domain.model.CheckoutState;
import com.irvinsegura.wallet.payments.domain.model.Payment;
import com.irvinsegura.wallet.payments.domain.model.PaymentAmountMismatchException;
import com.irvinsegura.wallet.payments.domain.port.PaymentRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every change of a payment's state, each under the payment's row lock and together with the event it publishes.
 * Webhooks, the stale payment job and the start of a payment all end up here, so whichever comes first wins and
 * the others change nothing.
 */
@Component
class PaymentTransitions {

    private static final Logger log = LoggerFactory.getLogger(PaymentTransitions.class);

    private final PaymentRepository repository;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    PaymentTransitions(PaymentRepository repository, ApplicationEventPublisher events, Clock clock) {
        this.repository = repository;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public PaymentSession open(UUID paymentId, CheckoutSession session) {
        Payment payment = lock(paymentId).orElseThrow();
        Instant now = clock.instant();
        payment.open(session, now);
        repository.save(payment);
        if (payment.isCollected()) {
            publishSucceeded(payment, now);
        }
        return new PaymentSession(paymentId, payment.isCollected(), payment.checkoutUrl(), payment.expiresAt());
    }

    @Transactional
    public boolean succeed(UUID paymentId, CheckoutState checkout) {
        Optional<Payment> found = lock(paymentId);
        if (found.isEmpty()) {
            return false;
        }
        Payment payment = found.get();
        Instant now = clock.instant();
        try {
            if (!payment.succeed(checkout.providerPaymentId(), checkout.amount(), now)) {
                return false;
            }
        } catch (PaymentAmountMismatchException e) {
            // Left as it is for a person to look at; raising it would only make the provider redeliver the webhook
            log.error("Not crediting checkout {}: {}", checkout.reference(), e.getMessage());
            return false;
        }
        repository.save(payment);
        publishSucceeded(payment, now);
        return true;
    }

    @Transactional
    public boolean expire(UUID paymentId) {
        return close(paymentId, PaymentFailed.EXPIRED);
    }

    @Transactional
    public boolean fail(UUID paymentId, String reason) {
        return close(paymentId, reason);
    }

    private boolean close(UUID paymentId, String reason) {
        Optional<Payment> found = lock(paymentId);
        if (found.isEmpty()) {
            return false;
        }
        Payment payment = found.get();
        Instant now = clock.instant();
        boolean changed = PaymentFailed.EXPIRED.equals(reason) ? payment.expire(now) : payment.fail(now);
        if (!changed) {
            return false;
        }
        repository.save(payment);
        events.publishEvent(new PaymentFailed(paymentId, reason, now));
        return true;
    }

    @Transactional
    public boolean requestRefund(UUID paymentId) {
        Optional<Payment> found = lock(paymentId);
        if (found.isEmpty()) {
            return false;
        }
        Payment payment = found.get();
        Instant now = clock.instant();
        if (!payment.requestRefund(now)) {
            return false;
        }
        repository.save(payment);
        events.publishEvent(new RefundRequested(paymentId, now));
        return true;
    }

    @Transactional
    public void markRefunded(UUID paymentId, String refundId) {
        Payment payment = lock(paymentId).orElseThrow();
        if (payment.markRefunded(refundId, clock.instant())) {
            repository.save(payment);
            log.info("Payment {} refunded as {}", paymentId, refundId);
        }
    }

    // Provider events can mention checkouts this application never opened, e.g. test events sent from a dashboard
    private Optional<Payment> lock(UUID paymentId) {
        Optional<Payment> payment = paymentId == null ? Optional.empty() : repository.findByIdForUpdate(paymentId);
        if (payment.isEmpty()) {
            log.warn("Ignoring provider update for unknown payment {}", paymentId);
        }
        return payment;
    }

    private void publishSucceeded(Payment payment, Instant now) {
        events.publishEvent(new PaymentSucceeded(payment.id(), payment.amount().minorUnits(),
                payment.amount().currency().getCurrencyCode(), now));
    }
}
