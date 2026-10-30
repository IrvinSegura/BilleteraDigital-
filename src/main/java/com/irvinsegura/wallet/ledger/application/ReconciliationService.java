package com.irvinsegura.wallet.ledger.application;

import com.irvinsegura.wallet.ledger.domain.model.Discrepancy;
import com.irvinsegura.wallet.ledger.domain.model.ReconciliationInProgressException;
import com.irvinsegura.wallet.ledger.domain.model.ReconciliationRun;
import com.irvinsegura.wallet.ledger.domain.port.LedgerIntegrityChecks;
import com.irvinsegura.wallet.ledger.domain.port.ReconciliationRunRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proves the ledger is internally consistent: every cached balance equals the net of its postings, every journal
 * entry balances, and per currency the platform's assets equal what it owes users. Postings are append-only, so
 * a mismatch means a bug or a change made outside the application, never a normal state.
 */
@Service
public class ReconciliationService {

    // Stored per check; a broken ledger could otherwise produce millions of rows. The count says if there were more.
    static final int MAX_DISCREPANCIES_PER_CHECK = 100;

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final LedgerIntegrityChecks checks;
    private final ReconciliationRunRepository runRepository;
    private final Clock clock;

    ReconciliationService(LedgerIntegrityChecks checks, ReconciliationRunRepository runRepository, Clock clock) {
        this.checks = checks;
        this.runRepository = runRepository;
        this.clock = clock;
    }

    /**
     * Repeatable read gives every check the same snapshot while transfers keep running, without blocking them.
     *
     * @param triggeredBy administrator starting a manual run; null for scheduled runs
     * @throws ReconciliationInProgressException if another instance or request is reconciling right now
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public ReconciliationRun reconcile(ReconciliationRun.Trigger trigger, UUID triggeredBy) {
        if (!checks.tryLockForReconciliation()) {
            throw new ReconciliationInProgressException();
        }
        Instant startedAt = clock.instant();
        List<Discrepancy> discrepancies = new ArrayList<>();
        discrepancies.addAll(checks.findBalanceMismatches(MAX_DISCREPANCIES_PER_CHECK));
        discrepancies.addAll(checks.findUnbalancedEntries(MAX_DISCREPANCIES_PER_CHECK));
        discrepancies.addAll(checks.findTrialBalanceGaps());

        ReconciliationRun run = new ReconciliationRun(UUID.randomUUID(), trigger, triggeredBy, startedAt,
                clock.instant(), checks.countAccounts(), checks.countEntries(), discrepancies);
        runRepository.save(run);

        if (run.isConsistent()) {
            log.info("Ledger reconciled: {} accounts and {} entries consistent", run.accountsChecked(),
                    run.entriesChecked());
        } else {
            log.error("Ledger reconciliation {} found {} discrepancies: {}", run.id(), discrepancies.size(),
                    discrepancies);
        }
        return run;
    }

    @Transactional(readOnly = true)
    public List<ReconciliationRun> recentRuns(int limit) {
        return runRepository.findRecent(limit);
    }
}
