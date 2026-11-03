package com.irvinsegura.wallet.transfers.application;

import com.irvinsegura.wallet.transfers.domain.port.IdempotencyRepository;
import java.time.Clock;
import java.time.Duration;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keys only need to outlive client retries. After the retention period a reused key runs as a new request, which
 * is the documented contract; the operations themselves stay in their own tables and the ledger.
 */
@Service
public class IdempotencyKeyCleanupService {

    private final IdempotencyRepository idempotencyRepository;
    private final Clock clock;

    IdempotencyKeyCleanupService(IdempotencyRepository idempotencyRepository, Clock clock) {
        this.idempotencyRepository = idempotencyRepository;
        this.clock = clock;
    }

    /**
     * @return how many keys were deleted
     */
    @Transactional
    public int deleteOlderThan(Duration retention) {
        return idempotencyRepository.deleteCreatedBefore(clock.instant().minus(retention));
    }
}
