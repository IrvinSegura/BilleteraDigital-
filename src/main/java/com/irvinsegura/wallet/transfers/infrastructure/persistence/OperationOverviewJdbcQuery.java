package com.irvinsegura.wallet.transfers.infrastructure.persistence;

import com.irvinsegura.wallet.shared.domain.Money;
import com.irvinsegura.wallet.transfers.domain.model.OperationDailyTotal;
import com.irvinsegura.wallet.transfers.domain.model.OperationTotal;
import com.irvinsegura.wallet.transfers.domain.model.PendingOperation;
import com.irvinsegura.wallet.transfers.domain.port.OperationOverview;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Plain JDBC: each read spans the operation tables. Pending withdrawals have a partial index; pending deposits are
 * few, since they expire within hours.
 */
@Repository
class OperationOverviewJdbcQuery implements OperationOverview {

    private final JdbcClient jdbcClient;

    OperationOverviewJdbcQuery(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public List<PendingOperation> findPending(String kind, int limit) {
        return jdbcClient.sql("""
                        SELECT kind, id, user_id, wallet_id, amount_minor, currency, created_at FROM (
                            SELECT 'deposit' AS kind, id, user_id, wallet_id, amount_minor, currency, created_at
                            FROM deposits WHERE status = 'PENDING'
                            UNION ALL
                            SELECT 'withdrawal', id, user_id, wallet_id, amount_minor, currency, created_at
                            FROM withdrawals WHERE status = 'PENDING'
                        ) pending
                        WHERE CAST(:kind AS text) IS NULL OR kind = :kind
                        ORDER BY created_at, id
                        LIMIT :limit""")
                .param("kind", kind)
                .param("limit", limit)
                .query((rs, rowNum) -> new PendingOperation(rs.getString("kind"), rs.getObject("id", UUID.class),
                        rs.getObject("user_id", UUID.class), rs.getObject("wallet_id", UUID.class),
                        Money.ofMinor(rs.getLong("amount_minor"), rs.getString("currency")),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant()))
                .list();
    }

    @Override
    public boolean hasPending(UUID userId) {
        return jdbcClient.sql("""
                        SELECT EXISTS (SELECT 1 FROM deposits WHERE user_id = :userId AND status = 'PENDING')
                            OR EXISTS (SELECT 1 FROM withdrawals WHERE user_id = :userId AND status = 'PENDING')""")
                .param("userId", userId)
                .query(Boolean.class)
                .single();
    }

    @Override
    public List<OperationTotal> totalsSince(Instant since) {
        return jdbcClient.sql("""
                        SELECT kind, currency, count(*) AS operations, sum(amount_minor) AS amount_minor FROM (
                            SELECT 'transfer' AS kind, currency, amount_minor FROM transfers WHERE created_at >= :since
                            UNION ALL
                            SELECT 'deposit', currency, amount_minor FROM deposits
                            WHERE status = 'COMPLETED' AND closed_at >= :since
                            UNION ALL
                            SELECT 'withdrawal', currency, amount_minor FROM withdrawals
                            WHERE status = 'PAID' AND closed_at >= :since
                        ) finished
                        GROUP BY kind, currency
                        ORDER BY kind, currency""")
                .param("since", since.atOffset(ZoneOffset.UTC))
                .query((rs, rowNum) -> new OperationTotal(rs.getString("kind"), rs.getLong("operations"),
                        Money.ofMinor(rs.getLong("amount_minor"), rs.getString("currency"))))
                .list();
    }

    @Override
    public List<OperationDailyTotal> dailyTotalsSince(Instant since, ZoneId zone) {
        return jdbcClient.sql("""
                        SELECT to_char(finished_at AT TIME ZONE CAST(:zone AS text), 'YYYY-MM-DD') AS day, kind, currency,
                               count(*) AS operations, sum(amount_minor) AS amount_minor FROM (
                            SELECT 'transfer' AS kind, currency, amount_minor, created_at AS finished_at
                            FROM transfers WHERE created_at >= :since
                            UNION ALL
                            SELECT 'deposit', currency, amount_minor, closed_at FROM deposits
                            WHERE status = 'COMPLETED' AND closed_at >= :since
                            UNION ALL
                            SELECT 'withdrawal', currency, amount_minor, closed_at FROM withdrawals
                            WHERE status = 'PAID' AND closed_at >= :since
                        ) finished
                        GROUP BY day, kind, currency
                        ORDER BY day, kind, currency""")
                .param("zone", zone.getId())
                .param("since", since.atOffset(ZoneOffset.UTC))
                .query((rs, rowNum) -> new OperationDailyTotal(LocalDate.parse(rs.getString("day")),
                        rs.getString("kind"), rs.getLong("operations"),
                        Money.ofMinor(rs.getLong("amount_minor"), rs.getString("currency"))))
                .list();
    }
}
