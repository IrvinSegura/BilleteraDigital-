package com.irvinsegura.wallet.transfers.application;

import com.irvinsegura.wallet.identity.AccountClosureCheck;
import com.irvinsegura.wallet.transfers.domain.port.OperationOverview;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * A deposit or a withdrawal still waiting would settle into, or bounce back to, an account that no longer exists.
 */
@Component
class PendingOperationsClosureCheck implements AccountClosureCheck {

    static final String PENDING_OPERATIONS = "PENDING_OPERATIONS";

    private final OperationOverview overview;

    PendingOperationsClosureCheck(OperationOverview overview) {
        this.overview = overview;
    }

    @Override
    public Optional<String> blocker(UUID userId) {
        return overview.hasPending(userId) ? Optional.of(PENDING_OPERATIONS) : Optional.empty();
    }
}
