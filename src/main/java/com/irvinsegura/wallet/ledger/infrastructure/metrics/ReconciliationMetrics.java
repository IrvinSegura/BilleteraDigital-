package com.irvinsegura.wallet.ledger.infrastructure.metrics;

import com.irvinsegura.wallet.ledger.domain.port.ReconciliationRunRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

/**
 * Gauges read the latest stored run on each scrape, so every instance reports the same values no matter which one
 * ran the job, and alerts keep firing until a clean run replaces a bad one. Before the first run both are NaN.
 */
@Component
class ReconciliationMetrics implements MeterBinder {

    private final ReconciliationRunRepository runRepository;

    ReconciliationMetrics(ReconciliationRunRepository runRepository) {
        this.runRepository = runRepository;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("wallet.ledger.reconciliation.discrepancies", runRepository,
                        repository -> repository.findLatest()
                                .map(run -> (double) run.discrepancyCount())
                                .orElse(Double.NaN))
                .description("Discrepancies found by the latest ledger reconciliation")
                .register(registry);
        // Alert when it gets old: a stuck or failing job is as bad as a discrepancy nobody sees
        Gauge.builder("wallet.ledger.reconciliation.last.run", runRepository,
                        repository -> repository.findLatest()
                                .map(run -> run.finishedAt().getEpochSecond() + run.finishedAt().getNano() / 1e9)
                                .orElse(Double.NaN))
                .description("When the latest ledger reconciliation finished, as a Unix timestamp")
                .baseUnit("seconds")
                .register(registry);
    }
}
