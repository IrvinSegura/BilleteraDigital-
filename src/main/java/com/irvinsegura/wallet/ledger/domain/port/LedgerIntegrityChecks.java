package com.irvinsegura.wallet.ledger.domain.port;

import com.irvinsegura.wallet.ledger.domain.model.Discrepancy;
import java.util.List;

/**
 * Read-only checks over the whole ledger. Callers run them in one repeatable-read transaction, so all of them see
 * the same snapshot and entries committed meanwhile cannot cause false alarms.
 */
public interface LedgerIntegrityChecks {

    /**
     * Takes a lock held until the current transaction ends, so only one instance reconciles at a time.
     *
     * @return false if another transaction holds it
     */
    boolean tryLockForReconciliation();

    long countAccounts();

    long countEntries();

    List<Discrepancy> findBalanceMismatches(int limit);

    List<Discrepancy> findUnbalancedEntries(int limit);

    List<Discrepancy> findTrialBalanceGaps();
}
