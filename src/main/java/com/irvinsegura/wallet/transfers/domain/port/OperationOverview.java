package com.irvinsegura.wallet.transfers.domain.port;

import com.irvinsegura.wallet.transfers.domain.model.OperationDailyTotal;
import com.irvinsegura.wallet.transfers.domain.model.OperationTotal;
import com.irvinsegura.wallet.transfers.domain.model.PendingOperation;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * The reads behind the administration overview: what is still waiting and what got done.
 */
public interface OperationOverview {

    /**
     * Deposits and withdrawals still pending, the oldest first: the ones most likely stuck.
     *
     * @param kind {@code deposit} or {@code withdrawal}; null for both
     */
    List<PendingOperation> findPending(String kind, int limit);

    /**
     * Completed deposits, paid withdrawals and transfers that finished at or after {@code since}, by kind and currency.
     */
    List<OperationTotal> totalsSince(Instant since);

    /**
     * The same, by day in {@code zone}, the oldest first. Days with nothing have no row.
     */
    List<OperationDailyTotal> dailyTotalsSince(Instant since, ZoneId zone);

    /**
     * Whether the user has a deposit or a withdrawal still waiting.
     */
    boolean hasPending(UUID userId);
}
