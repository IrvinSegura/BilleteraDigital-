package com.irvinsegura.wallet.transfers.application;

import com.irvinsegura.wallet.transfers.domain.model.PayoutProviderUnavailableException;
import com.irvinsegura.wallet.transfers.domain.model.WithdrawalStatus;
import com.irvinsegura.wallet.transfers.domain.port.WithdrawalRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Moves pending withdrawals forward: sends the payouts the provider never received (it was down, or the instance
 * stopped right after the request) and asks about the ones still on their way.
 */
@Service
public class PendingWithdrawalService {

    private static final Logger log = LoggerFactory.getLogger(PendingWithdrawalService.class);
    private static final int BATCH_SIZE = 100;

    private final WithdrawalRepository withdrawalRepository;
    private final WithdrawalSettlement settlement;
    private final Clock clock;
    private final Duration retryAfter;

    PendingWithdrawalService(WithdrawalRepository withdrawalRepository, WithdrawalSettlement settlement, Clock clock,
            @Value("${wallet.transfers.payouts.retry-after}") Duration retryAfter) {
        this.withdrawalRepository = withdrawalRepository;
        this.settlement = settlement;
        this.clock = clock;
        this.retryAfter = retryAfter;
    }

    /**
     * Returns how many withdrawals were paid or failed.
     */
    public int advancePendingWithdrawals() {
        // Leaves the newest ones to the request that is still sending them
        int settled = 0;
        for (UUID withdrawalId : withdrawalRepository.findPendingCreatedBefore(clock.instant().minus(retryAfter),
                BATCH_SIZE)) {
            try {
                if (settlement.advance(withdrawalId).status() != WithdrawalStatus.PENDING) {
                    settled++;
                }
            } catch (PayoutProviderUnavailableException e) {
                log.warn("Could not reach the payout provider for withdrawal {}; trying again on the next run",
                        withdrawalId);
            }
        }
        return settled;
    }
}
