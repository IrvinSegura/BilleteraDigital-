package com.irvinsegura.wallet.transfers.application;

import com.irvinsegura.wallet.payments.PaymentFailed;
import com.irvinsegura.wallet.payments.PaymentSucceeded;
import com.irvinsegura.wallet.transfers.domain.model.DepositStatus;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Settles deposits when the payment provider reports back. Events stay in the outbox until this succeeds, so a
 * deposit is never left pending because the instance stopped after the payment was confirmed.
 */
@Component
class DepositPaymentListener {

    private final DepositSettlement settlement;

    DepositPaymentListener(DepositSettlement settlement) {
        this.settlement = settlement;
    }

    // Already completed by the request itself when the payment succeeded right away; then this changes nothing
    @ApplicationModuleListener
    void on(PaymentSucceeded event) {
        settlement.complete(event.paymentId());
    }

    @ApplicationModuleListener
    void on(PaymentFailed event) {
        settlement.abandon(event.paymentId(),
                PaymentFailed.EXPIRED.equals(event.reason()) ? DepositStatus.EXPIRED : DepositStatus.FAILED);
    }
}
