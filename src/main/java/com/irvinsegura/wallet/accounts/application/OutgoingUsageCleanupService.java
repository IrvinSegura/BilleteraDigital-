package com.irvinsegura.wallet.accounts.application;

import com.irvinsegura.wallet.accounts.domain.model.DailyLimitPolicy;
import com.irvinsegura.wallet.accounts.domain.port.OutgoingUsageRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Only today's usage matters for the limit; older days are kept a while for support questions and then deleted.
 * The audit log keeps the operations themselves.
 */
@Service
public class OutgoingUsageCleanupService {

    private final OutgoingUsageRepository usageRepository;
    private final DailyLimitPolicy dailyLimitPolicy;
    private final Clock clock;

    OutgoingUsageCleanupService(OutgoingUsageRepository usageRepository, DailyLimitPolicy dailyLimitPolicy,
            Clock clock) {
        this.usageRepository = usageRepository;
        this.dailyLimitPolicy = dailyLimitPolicy;
        this.clock = clock;
    }

    /**
     * @return how many wallet-day rows were deleted
     */
    @Transactional
    public int deleteOlderThan(Period retention) {
        LocalDate today = dailyLimitPolicy.dayOf(clock.instant());
        return usageRepository.deleteBefore(today.minus(retention));
    }
}
