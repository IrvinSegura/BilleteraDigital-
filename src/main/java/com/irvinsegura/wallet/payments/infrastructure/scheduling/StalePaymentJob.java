package com.irvinsegura.wallet.payments.infrastructure.scheduling;

import com.irvinsegura.wallet.payments.application.StalePaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Instances running it at the same time is harmless: each payment changes state under its row lock, so the second
 * one to get there finds nothing left to do.
 */
@Component
class StalePaymentJob {

    private static final Logger log = LoggerFactory.getLogger(StalePaymentJob.class);

    private final StalePaymentService stalePaymentService;

    StalePaymentJob(StalePaymentService stalePaymentService) {
        this.stalePaymentService = stalePaymentService;
    }

    @Scheduled(fixedDelayString = "${wallet.payments.stale.check-interval}")
    void resolveStalePayments() {
        int resolved = stalePaymentService.resolveStalePayments();
        if (resolved > 0) {
            log.info("Settled {} payments whose webhook never arrived", resolved);
        }
    }
}
