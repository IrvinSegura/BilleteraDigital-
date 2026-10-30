package com.irvinsegura.wallet.transfers.infrastructure.metrics;

import com.irvinsegura.wallet.shared.domain.Money;
import com.irvinsegura.wallet.transfers.DepositCompleted;
import com.irvinsegura.wallet.transfers.TransferCompleted;
import com.irvinsegura.wallet.transfers.WithdrawalCompleted;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Counts money movements and their amounts, per type and currency. The count and sum of
 * {@code wallet.money.moved} give operations per minute and volume moved.
 * <p>
 * A plain listener plus an after-commit callback, rather than a transactional event listener: those are stored in
 * the event outbox, which would add a database write per operation and count again on every restart replay.
 */
@Component
class MoneyMovementMetrics {

    private final MeterRegistry meterRegistry;

    MoneyMovementMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @EventListener
    void on(DepositCompleted event) {
        afterCommit("deposit", event.amountMinor(), event.currency());
    }

    @EventListener
    void on(WithdrawalCompleted event) {
        afterCommit("withdrawal", event.amountMinor(), event.currency());
    }

    @EventListener
    void on(TransferCompleted event) {
        afterCommit("transfer", event.amountMinor(), event.currency());
    }

    // Counting before commit would include operations that are rolled back afterwards
    private void afterCommit(String type, long amountMinor, String currency) {
        Runnable record = () -> DistributionSummary.builder("wallet.money.moved")
                .description("Money moved by completed operations, in major units of the currency tag")
                .tag("type", type)
                .tag("currency", currency)
                .register(meterRegistry)
                .record(Money.ofMinor(amountMinor, currency).toDecimal().doubleValue());
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    record.run();
                }
            });
        } else {
            record.run();
        }
    }
}
