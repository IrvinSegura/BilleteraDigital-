package com.irvinsegura.wallet.transfers.infrastructure.payouts;

import com.irvinsegura.wallet.transfers.domain.port.PayoutGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Picks the payout provider. Startup fails without one, so a production deployment never pays withdrawals with the
 * simulated provider by accident.
 */
@Configuration(proxyBeanMethods = false)
class PayoutsConfiguration {

    @Bean
    PayoutGateway payoutGateway(@Value("${wallet.transfers.payouts.provider:}") String provider) {
        if ("simulated".equals(provider)) {
            return new SimulatedPayoutGateway();
        }
        throw new IllegalStateException(("wallet.transfers.payouts.provider (PAYOUT_PROVIDER) is '%s'; the only "
                + "provider so far is 'simulated', which sends no real money, so set it only for demo deployments")
                .formatted(provider));
    }
}
