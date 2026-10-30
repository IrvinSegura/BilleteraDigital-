package com.irvinsegura.wallet.ledger.domain.port;

import com.irvinsegura.wallet.ledger.domain.model.ReconciliationRun;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ReconciliationRunRepository {

    void save(ReconciliationRun run);

    /**
     * Runs with their discrepancies, newest first.
     */
    List<ReconciliationRun> findRecent(int limit);

    /**
     * Latest run without its discrepancies, only their count; cheap enough to call on every metrics scrape.
     */
    Optional<LatestRun> findLatest();

    record LatestRun(Instant finishedAt, int discrepancyCount) {
    }
}
