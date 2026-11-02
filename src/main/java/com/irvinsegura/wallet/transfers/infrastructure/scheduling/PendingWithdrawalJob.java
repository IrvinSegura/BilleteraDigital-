package com.irvinsegura.wallet.transfers.infrastructure.scheduling;

import com.irvinsegura.wallet.transfers.application.PendingWithdrawalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Instances running it at the same time is harmless: sending a payout again returns the one already sent, and each
 * withdrawal is settled under its row lock, so the second instance to get there finds nothing left to do.
 */
@Component
class PendingWithdrawalJob {

    private static final Logger log = LoggerFactory.getLogger(PendingWithdrawalJob.class);

    private final PendingWithdrawalService pendingWithdrawalService;

    PendingWithdrawalJob(PendingWithdrawalService pendingWithdrawalService) {
        this.pendingWithdrawalService = pendingWithdrawalService;
    }

    @Scheduled(fixedDelayString = "${wallet.transfers.payouts.check-interval}")
    void advancePendingWithdrawals() {
        int settled = pendingWithdrawalService.advancePendingWithdrawals();
        if (settled > 0) {
            log.info("Settled {} pending withdrawals", settled);
        }
    }
}
