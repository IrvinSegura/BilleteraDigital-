package com.irvinsegura.wallet.ledger.infrastructure.scheduling;

import com.irvinsegura.wallet.ledger.application.ReconciliationService;
import com.irvinsegura.wallet.ledger.domain.model.ReconciliationInProgressException;
import com.irvinsegura.wallet.ledger.domain.model.ReconciliationRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Every instance schedules the job; the database lock makes all but one of them skip each run.
 */
@Component
class ReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationJob.class);

    private final ReconciliationService reconciliationService;

    ReconciliationJob(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @Scheduled(cron = "${wallet.ledger.reconciliation.cron}")
    void reconcile() {
        try {
            reconciliationService.reconcile(ReconciliationRun.Trigger.SCHEDULED, null);
        } catch (ReconciliationInProgressException e) {
            log.info("Skipping scheduled reconciliation: another one is running");
        }
    }
}
