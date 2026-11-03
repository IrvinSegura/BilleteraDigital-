package com.irvinsegura.wallet.payments.infrastructure.metrics;

import com.irvinsegura.wallet.payments.application.ProviderBalanceService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Whether the clearing account follows the provider: movements still waiting to be recorded, movements nobody could
 * match, and the gap between the provider's balance and the clearing account per currency.
 */
@Component
class ProviderBalanceMetrics implements MeterBinder {

    // The currencies the ledger has system accounts for
    private static final List<String> CURRENCIES = List.of("MXN", "USD");

    private final JdbcClient jdbcClient;
    private final ProviderBalanceService providerBalanceService;

    ProviderBalanceMetrics(JdbcClient jdbcClient, ProviderBalanceService providerBalanceService) {
        this.jdbcClient = jdbcClient;
        this.providerBalanceService = providerBalanceService;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        for (String status : List.of("PENDING", "UNMATCHED")) {
            Gauge.builder("wallet.provider.transactions", jdbcClient, client -> client.sql("""
                                    SELECT count(*) FROM provider_balance_transactions WHERE status = :status""")
                            .param("status", status)
                            .query(Long.class)
                            .single())
                    .description("Provider balance transactions not recorded in the ledger, by status")
                    .tag("status", status.toLowerCase(Locale.ROOT))
                    .register(registry);
        }
        // NaN until the first comparison, e.g. with a provider that does not hold money
        for (String currency : CURRENCIES) {
            Gauge.builder("wallet.provider.balance.difference", providerBalanceService,
                            service -> {
                                Long difference = service.balanceDifferences().get(currency);
                                return difference == null ? Double.NaN : difference;
                            })
                    .description("Provider balance minus the clearing account, in minor units")
                    .tag("currency", currency)
                    .register(registry);
        }
    }
}
