package com.irvinsegura.wallet.transfers.infrastructure.persistence;

import com.irvinsegura.wallet.shared.domain.Money;
import com.irvinsegura.wallet.transfers.CustomerOperations;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Plain JDBC: one read across the operation tables, each limited before they are put together. Adjustments count
 * once applied: until then no money moved.
 */
@Repository
class CustomerOperationsJdbcQuery implements CustomerOperations {

    // A received transfer is the recipient's too; transfers have no closing time, they finish as they start
    private static final String OPERATIONS = """
            SELECT * FROM (
                (SELECT 'deposit' AS kind, id, status, amount_minor, currency, rejection_reason AS reason, created_at,
                        closed_at
                 FROM deposits WHERE user_id = :userId %1$s ORDER BY created_at DESC LIMIT :limit)
                UNION ALL
                (SELECT 'withdrawal', id, status, amount_minor, currency, failure_reason, created_at, closed_at
                 FROM withdrawals WHERE user_id = :userId %1$s ORDER BY created_at DESC LIMIT :limit)
                UNION ALL
                (SELECT 'transfer_out', id, 'COMPLETED', amount_minor, currency, NULL, created_at, created_at
                 FROM transfers WHERE sender_id = :userId %1$s ORDER BY created_at DESC LIMIT :limit)
                UNION ALL
                (SELECT 'transfer_in', id, 'COMPLETED', amount_minor, currency, NULL, created_at, created_at
                 FROM transfers WHERE recipient_id = :userId %1$s ORDER BY created_at DESC LIMIT :limit)
                UNION ALL
                (SELECT CASE direction WHEN 'CREDIT' THEN 'adjustment_credit' ELSE 'adjustment_debit' END, id,
                        'COMPLETED', amount_minor, currency, reason, requested_at, decided_at
                 FROM adjustments WHERE user_id = :userId AND status = 'APPLIED' %1$s
                 ORDER BY requested_at DESC LIMIT :limit)
            ) operations
            ORDER BY created_at DESC, id
            LIMIT :limit""";

    private final JdbcClient jdbcClient;

    CustomerOperationsJdbcQuery(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    @Transactional(readOnly = true)
    public List<OperationBrief> recent(UUID userId, int limit) {
        return jdbcClient.sql(OPERATIONS.formatted(""))
                .param("userId", userId)
                .param("limit", limit)
                .query((rs, rowNum) -> brief(rs))
                .list();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OperationBrief> find(UUID userId, UUID operationId) {
        return jdbcClient.sql(OPERATIONS.formatted("AND id = :operationId"))
                .param("userId", userId)
                .param("operationId", operationId)
                .param("limit", 1)
                .query((rs, rowNum) -> brief(rs))
                .optional();
    }

    private static OperationBrief brief(ResultSet rs) throws SQLException {
        return new OperationBrief(rs.getString("kind"), rs.getObject("id", UUID.class), rs.getString("status"),
                Money.ofMinor(rs.getLong("amount_minor"), rs.getString("currency")), rs.getString("reason"),
                instant(rs, "created_at"), instant(rs, "closed_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
