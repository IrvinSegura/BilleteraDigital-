package com.irvinsegura.wallet.payments.application;

import com.irvinsegura.wallet.payments.domain.model.Payment;
import com.irvinsegura.wallet.payments.domain.model.PaymentStatus;
import com.irvinsegura.wallet.payments.domain.port.PaymentGateway;
import com.irvinsegura.wallet.payments.domain.port.PaymentRepository;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Calls the provider once the refund request is committed. If the provider fails, the event stays in the outbox
 * and is retried with backoff; the provider sees the same idempotency key every time, so it refunds once.
 */
@Component
class RefundListener {

    private final PaymentRepository repository;
    private final PaymentGateway gateway;
    private final PaymentTransitions transitions;

    RefundListener(PaymentRepository repository, PaymentGateway gateway, PaymentTransitions transitions) {
        this.repository = repository;
        this.gateway = gateway;
        this.transitions = transitions;
    }

    @ApplicationModuleListener
    void on(RefundRequested event) {
        Payment payment = repository.findById(event.paymentId()).orElseThrow();
        if (payment.status() != PaymentStatus.REFUND_PENDING) {
            return;
        }
        transitions.markRefunded(payment.id(), gateway.refund(payment));
    }
}
