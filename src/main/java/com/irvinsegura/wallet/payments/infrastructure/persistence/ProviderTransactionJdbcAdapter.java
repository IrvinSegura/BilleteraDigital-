package com.irvinsegura.wallet.payments.infrastructure.persistence;

import com.irvinsegura.wallet.payments.domain.model.BalanceTransaction;
import com.irvinsegura.wallet.payments.domain.model.ProviderTransaction;
import com.irvinsegura.wallet.payments.domain.port.ProviderTransactionRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Plain JDBC: "insert unless it exists" needs ON CONFLICT, and rows are only ever read whole or settled.
 */
@Repository
class ProviderTransactionJdbcAdapter implements ProviderTransactionRepository {

    private static final String COLUMNS = """
            provider, id, type, provider_type, amount_minor, fee_minor, currency, provider_payment_id, status, note,
            created_at, recorded_at, settled_at, resolved_by, resolution_reason, resolved_at""";

    private final JdbcClient jdbcClient;

    ProviderTransactionJdbcAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public boolean recordIfNew(String provider, BalanceTransaction transaction, Instant now) {
        return jdbcClient.sql("""
                        INSERT INTO provider_balance_transactions (provider, id, type, provider_type, amount_minor,
                            fee_minor, currency, provider_payment_id, status, created_at, recorded_at, version)
                        VALUES (:provider, :id, :type, :providerType, :amount, :fee, :currency, :paymentId, 'PENDING',
                            :createdAt, :recordedAt, 0)
                        ON CONFLICT (provider, id) DO NOTHING""")
                .param("provider", provider)
                .param("id", transaction.id())
                .param("type", transaction.type().name())
                .param("providerType", transaction.providerType())
                .param("amount", transaction.amountMinor())
                .param("fee", transaction.feeMinor())
                .param("currency", transaction.currency())
                .param("paymentId", transaction.providerPaymentId())
                .param("createdAt", utc(transaction.createdAt()))
                .param("recordedAt", utc(now))
                .update() == 1;
    }

    @Override
    public Optional<Instant> findLatestCreatedAt(String provider) {
        return jdbcClient.sql("SELECT max(created_at) FROM provider_balance_transactions WHERE provider = :provider")
                .param("provider", provider)
                .query(OffsetDateTime.class)
                .list().stream()
                // max() of no rows is one null row
                .filter(Objects::nonNull)
                .findFirst()
                .map(OffsetDateTime::toInstant);
    }

    @Override
    public List<String> findPendingIds(String provider, int limit) {
        return jdbcClient.sql("""
                        SELECT id FROM provider_balance_transactions
                        WHERE provider = :provider AND status = 'PENDING'
                        ORDER BY created_at, id
                        LIMIT :limit""")
                .param("provider", provider)
                .param("limit", limit)
                .query(String.class)
                .list();
    }

    @Override
    public Optional<ProviderTransaction> findByIdForUpdate(String provider, String id) {
        return jdbcClient.sql("""
                        SELECT %s FROM provider_balance_transactions
                        WHERE provider = :provider AND id = :id FOR UPDATE""".formatted(COLUMNS))
                .param("provider", provider)
                .param("id", id)
                .query(ProviderTransactionJdbcAdapter::map)
                .optional();
    }

    @Override
    public void settle(String provider, String id, ProviderTransaction.Status status, String note, Instant now) {
        jdbcClient.sql("""
                        UPDATE provider_balance_transactions
                        SET status = :status, note = :note, settled_at = :settledAt, version = version + 1
                        WHERE provider = :provider AND id = :id""")
                .param("status", status.name())
                .param("note", note)
                .param("settledAt", utc(now))
                .param("provider", provider)
                .param("id", id)
                .update();
    }

    @Override
    public void resolve(String provider, String id, UUID resolvedBy, String reason, Instant now) {
        jdbcClient.sql("""
                        UPDATE provider_balance_transactions
                        SET status = 'RESOLVED', resolved_by = :resolvedBy, resolution_reason = :reason,
                            resolved_at = :resolvedAt, version = version + 1
                        WHERE provider = :provider AND id = :id""")
                .param("resolvedBy", resolvedBy)
                .param("reason", reason)
                .param("resolvedAt", utc(now))
                .param("provider", provider)
                .param("id", id)
                .update();
    }

    @Override
    public List<ProviderTransaction> find(ProviderTransaction.Status status, int limit) {
        // Casting the parameter lets PostgreSQL type a null filter, which it cannot infer on its own
        return jdbcClient.sql("""
                        SELECT %s FROM provider_balance_transactions
                        WHERE (CAST(:status AS VARCHAR) IS NULL OR status = :status)
                        ORDER BY created_at DESC, id LIMIT :limit""".formatted(COLUMNS))
                .param("status", status == null ? null : status.name())
                .param("limit", limit)
                .query(ProviderTransactionJdbcAdapter::map)
                .list();
    }

    @Override
    public long count(ProviderTransaction.Status status) {
        return jdbcClient.sql("SELECT count(*) FROM provider_balance_transactions WHERE status = :status")
                .param("status", status.name())
                .query(Long.class)
                .single();
    }

    private static ProviderTransaction map(ResultSet rs, int rowNum) throws SQLException {
        BalanceTransaction transaction = new BalanceTransaction(rs.getString("id"),
                BalanceTransaction.Type.valueOf(rs.getString("type")), rs.getString("provider_type"),
                rs.getLong("amount_minor"), rs.getLong("fee_minor"), rs.getString("currency"),
                rs.getString("provider_payment_id"), instant(rs, "created_at"));
        return new ProviderTransaction(rs.getString("provider"), transaction,
                ProviderTransaction.Status.valueOf(rs.getString("status")), rs.getString("note"),
                instant(rs, "recorded_at"), instant(rs, "settled_at"), resolution(rs));
    }

    private static ProviderTransaction.Resolution resolution(ResultSet rs) throws SQLException {
        Instant resolvedAt = instant(rs, "resolved_at");
        return resolvedAt == null ? null : new ProviderTransaction.Resolution(rs.getObject("resolved_by", UUID.class),
                rs.getString("resolution_reason"), resolvedAt);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
