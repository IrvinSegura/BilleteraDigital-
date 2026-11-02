package com.irvinsegura.wallet.transfers.infrastructure.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Payouts the bank has not confirmed yet. Money in transit balances the ledger, so the reconciliation never flags a
 * stuck payout: a growing age here is the only sign that the provider is down or a payout was lost.
 */
@Component
class PendingWithdrawalMetrics implements MeterBinder {

    private final JdbcClient jdbcClient;

    PendingWithdrawalMetrics(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    // Both queries use the partial index on pending withdrawals
    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("wallet.withdrawals.pending", jdbcClient, client -> client.sql("""
                                SELECT count(*) FROM withdrawals WHERE status = 'PENDING'""")
                        .query(Long.class)
                        .single())
                .description("Withdrawals whose payout the bank has not confirmed or refused yet")
                .register(registry);
        Gauge.builder("wallet.withdrawals.oldest.pending.age", jdbcClient, client -> client.sql("""
                                SELECT COALESCE(EXTRACT(EPOCH FROM now() - min(created_at)), 0)
                                FROM withdrawals WHERE status = 'PENDING'""")
                        .query(Double.class)
                        .single())
                .description("Age of the oldest withdrawal whose payout the bank has not confirmed or refused")
                .baseUnit("seconds")
                .register(registry);
    }
}
