package com.irvinsegura.wallet.support.infrastructure.scheduling;

import com.irvinsegura.wallet.support.application.SupportInactivityService;
import com.irvinsegura.wallet.support.application.SupportInactivityService.Outcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * See {@link SupportInactivityService#handleInactiveCases()} for why instances running it at once is harmless.
 */
@Component
class InactiveCaseJob {

    private static final Logger log = LoggerFactory.getLogger(InactiveCaseJob.class);

    private final SupportInactivityService inactivity;

    InactiveCaseJob(SupportInactivityService inactivity) {
        this.inactivity = inactivity;
    }

    @Scheduled(fixedDelayString = "${wallet.support.inactivity.check-interval}")
    void handleInactiveCases() {
        Outcome outcome = inactivity.handleInactiveCases();
        if (outcome.warned() > 0 || outcome.closed() > 0) {
            log.info("Warned {} customers who stopped answering and closed {} of their cases", outcome.warned(),
                    outcome.closed());
        }
    }
}
