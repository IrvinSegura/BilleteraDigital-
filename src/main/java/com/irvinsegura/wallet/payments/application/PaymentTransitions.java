package com.irvinsegura.wallet.payments.application;

import com.irvinsegura.wallet.ledger.Ledger;
import com.irvinsegura.wallet.ledger.NewJournalEntry;
import com.irvinsegura.wallet.ledger.RecordedEntry;
import com.irvinsegura.wallet.payments.PaymentFailed;
import com.irvinsegura.wallet.payments.PaymentSession;
import com.irvinsegura.wallet.payments.PaymentSucceeded;
import com.irvinsegura.wallet.payments.domain.model.CheckoutSession;
import com.irvinsegura.wallet.payments.domain.model.CheckoutState;
import com.irvinsegura.wallet.payments.domain.model.Payment;
import com.irvinsegura.wallet.payments.domain.model.PaymentAmountMismatchException;
import com.irvinsegura.wallet.payments.domain.port.PaymentGateway;
import com.irvinsegura.wallet.payments.domain.port.PaymentRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Currency;
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
 * <p>
 * Collected money is recorded in the ledger as soon as the payment succeeds, into unapplied payments: a deposit then
 * moves it to the wallet, or a refund sends it back.
 */
@Component
class PaymentTransitions {

    static final String RECEIVED_ENTRY_TYPE = "PAYMENT_RECEIVED";
    static final String REFUNDED_ENTRY_TYPE = "PAYMENT_REFUNDED";

    private static final Logger log = LoggerFactory.getLogger(PaymentTransitions.class);

    private final PaymentRepository repository;
    private final PaymentGateway gateway;
    private final Ledger ledger;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    PaymentTransitions(PaymentRepository repository, PaymentGateway gateway, Ledger ledger,
            ApplicationEventPublisher events, Clock clock) {
        this.repository = repository;
        this.gateway = gateway;
        this.ledger = ledger;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public PaymentSession open(UUID paymentId, CheckoutSession session) {
        Payment payment = lock(paymentId).orElseThrow();
        Instant now = clock.instant();
        payment.open(session, now);
        if (payment.isCollected()) {
            recordReceipt(payment);
        }
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
        recordReceipt(payment);
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
            // Payments collected before receipts were recorded never reached unapplied payments
            if (payment.receivedEntryId() != null) {
                recordRefund(payment);
            }
            repository.save(payment);
            log.info("Payment {} refunded as {}", paymentId, refundId);
        }
    }

    private void recordRefund(Payment payment) {
        Currency currency = payment.amount().currency();
        UUID from = fundsAccount(currency);
        // The provider may already have paid this money out to the bank; it then takes the refund from the bank
        // account it pays out to, so the ledger does too instead of failing the refund forever
        if (gateway.holdsFunds() && !ledger.balanceOf(from).isGreaterThanOrEqualTo(payment.amount())) {
            log.warn("Clearing account cannot cover the refund of payment {}; recording it against settlement",
                    payment.id());
            from = ledger.settlementAccount(currency);
        }
        ledger.record(new NewJournalEntry(REFUNDED_ENTRY_TYPE, payment.id().toString(),
                ledger.unappliedPaymentsAccount(currency), from, payment.amount()));
    }

    private void recordReceipt(Payment payment) {
        Currency currency = payment.amount().currency();
        RecordedEntry entry = ledger.record(new NewJournalEntry(RECEIVED_ENTRY_TYPE, payment.id().toString(),
                fundsAccount(currency), ledger.unappliedPaymentsAccount(currency), payment.amount()));
        payment.recordReceipt(entry.journalEntryId());
    }

    // Where collected money is until it is paid out: at the provider, or already in the partner bank
    private UUID fundsAccount(Currency currency) {
        return gateway.holdsFunds() ? ledger.providerClearingAccount(currency) : ledger.settlementAccount(currency);
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
