package com.irvinsegura.wallet.payments.application;

import com.irvinsegura.wallet.payments.PaymentFailed;
import com.irvinsegura.wallet.payments.PaymentProviderUnavailableException;
import com.irvinsegura.wallet.payments.PaymentRequest;
import com.irvinsegura.wallet.payments.PaymentSession;
import com.irvinsegura.wallet.payments.Payments;
import com.irvinsegura.wallet.payments.domain.model.CheckoutSession;
import com.irvinsegura.wallet.payments.domain.model.Payment;
import com.irvinsegura.wallet.payments.domain.model.PaymentStatus;
import com.irvinsegura.wallet.payments.domain.port.PaymentGateway;
import com.irvinsegura.wallet.payments.domain.port.PaymentRepository;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class PaymentService implements Payments {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository repository;
    private final PaymentGateway gateway;
    private final PaymentTransitions transitions;
    private final Clock clock;

    PaymentService(PaymentRepository repository, PaymentGateway gateway, PaymentTransitions transitions,
            Clock clock) {
        this.repository = repository;
        this.gateway = gateway;
        this.transitions = transitions;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void register(PaymentRequest request) {
        repository.save(Payment.register(request.paymentId(), request.userId(), request.amount(),
                request.description(), gateway.name(), clock.instant()));
    }

    @Override
    public PaymentSession start(UUID paymentId) {
        // A network call inside a transaction would hold its row locks and connection for as long as the provider takes
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Payments must be started outside a transaction");
        }
        Payment payment = repository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown payment " + paymentId));
        if (payment.status() != PaymentStatus.CREATED) {
            return new PaymentSession(paymentId, payment.isCollected(), payment.checkoutUrl(), payment.expiresAt());
        }
        CheckoutSession session;
        try {
            session = gateway.openCheckout(payment);
        } catch (PaymentProviderUnavailableException e) {
            // The adapter already logged what the provider answered
            log.warn("Could not open a checkout for payment {}; failing it", paymentId);
            transitions.fail(paymentId, PaymentFailed.PROVIDER_UNAVAILABLE);
            throw e;
        }
        return transitions.open(paymentId, session);
    }

    @Override
    @Transactional
    public void refund(UUID paymentId) {
        transitions.requestRefund(paymentId);
    }
}
