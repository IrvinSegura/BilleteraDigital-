package com.irvinsegura.wallet.transfers.infrastructure.persistence;

import com.irvinsegura.wallet.shared.domain.Money;
import com.irvinsegura.wallet.transfers.domain.model.OperationSuggestion;
import com.irvinsegura.wallet.transfers.domain.port.OperationSuggestions;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Plain JDBC: one read across the three operation tables. Comparing ids as text scans them, which is fine for a
 * support search run by a handful of administrators.
 */
@Repository
class OperationSuggestionJdbcQuery implements OperationSuggestions {

    private final JdbcClient jdbcClient;

    OperationSuggestionJdbcQuery(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public List<OperationSuggestion> find(String idStart, String kind, int limit) {
        return jdbcClient.sql("""
                        SELECT kind, id, amount_minor, currency, created_at FROM (
                            SELECT 'transfer' AS kind, id, amount_minor, currency, created_at FROM transfers
                            UNION ALL
                            SELECT 'deposit', id, amount_minor, currency, created_at FROM deposits
                            UNION ALL
                            SELECT 'withdrawal', id, amount_minor, currency, created_at FROM withdrawals
                        ) operations
                        WHERE id::text LIKE :idStart
                          AND (CAST(:kind AS text) IS NULL OR kind = :kind)
                        ORDER BY created_at DESC, id
                        LIMIT :limit""")
                .param("idStart", idStart + "%")
                .param("kind", kind)
                .param("limit", limit)
                .query((rs, rowNum) -> new OperationSuggestion(rs.getString("kind"), rs.getObject("id", UUID.class),
                        Money.ofMinor(rs.getLong("amount_minor"), rs.getString("currency")),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant()))
                .list();
    }
}
