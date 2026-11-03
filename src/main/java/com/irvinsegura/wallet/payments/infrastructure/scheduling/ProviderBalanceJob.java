package com.irvinsegura.wallet.payments.infrastructure.scheduling;

import com.irvinsegura.wallet.payments.PaymentProviderUnavailableException;
import com.irvinsegura.wallet.payments.application.ProviderBalanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Instances running it at the same time is harmless: see {@link ProviderBalanceService}.
 */
@Component
class ProviderBalanceJob {

    private static final Logger log = LoggerFactory.getLogger(ProviderBalanceJob.class);

    private final ProviderBalanceService providerBalanceService;

    ProviderBalanceJob(ProviderBalanceService providerBalanceService) {
        this.providerBalanceService = providerBalanceService;
    }

    @Scheduled(fixedDelayString = "${wallet.payments.provider-sync.interval}")
    void synchronize() {
        try {
            ProviderBalanceService.SyncResult result = providerBalanceService.synchronize();
            if (result.read() > 0 || result.posted() > 0 || result.unmatched() > 0) {
                log.info("Provider balance: {} transactions read, {} recorded, {} left for a person", result.read(),
                        result.posted(), result.unmatched());
            }
        } catch (PaymentProviderUnavailableException e) {
            // The adapter logged the details; the next run reads the same window again
            log.warn("Could not reach the payment provider to follow its balance; trying again on the next run");
        }
    }
}
