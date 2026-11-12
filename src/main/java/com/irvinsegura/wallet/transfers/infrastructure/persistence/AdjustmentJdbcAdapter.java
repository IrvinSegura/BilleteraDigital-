package com.irvinsegura.wallet.transfers.infrastructure.persistence;

import com.irvinsegura.wallet.shared.domain.Money;
import com.irvinsegura.wallet.transfers.domain.model.Adjustment;
import com.irvinsegura.wallet.transfers.domain.model.AdjustmentDirection;
import com.irvinsegura.wallet.transfers.domain.model.AdjustmentStatus;
import com.irvinsegura.wallet.transfers.domain.port.AdjustmentRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Plain JDBC, like the other administration reads of this module.
 */
@Repository
class AdjustmentJdbcAdapter implements AdjustmentRepository {

    private static final String COLUMNS = """
            id, wallet_id, user_id, direction, amount_minor, currency, reason, ticket_id, requested_by, requested_at,
            status, decided_by, decided_at, rejection_reason, journal_entry_id""";

    private final JdbcClient jdbcClient;

    AdjustmentJdbcAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public void insert(Adjustment adjustment) {
        jdbcClient.sql("""
                        INSERT INTO adjustments (id, wallet_id, user_id, direction, amount_minor, currency, reason,
                                                 ticket_id, requested_by, requested_at, status, decided_by,
                                                 decided_at, rejection_reason, journal_entry_id)
                        VALUES (:id, :walletId, :userId, :direction, :amountMinor, :currency, :reason, :ticketId,
                                :requestedBy, :requestedAt, :status, :decidedBy, :decidedAt, :rejectionReason,
                                :journalEntryId)""")
                .param("id", adjustment.id())
                .param("walletId", adjustment.walletId())
                .param("userId", adjustment.userId())
                .param("direction", adjustment.direction().name())
                .param("amountMinor", adjustment.amount().minorUnits())
                .param("currency", adjustment.amount().currency().getCurrencyCode())
                .param("reason", adjustment.reason())
                .param("ticketId", adjustment.ticketId())
                .param("requestedBy", adjustment.requestedBy())
                .param("requestedAt", utc(adjustment.requestedAt()))
                .param("status", adjustment.status().name())
                .param("decidedBy", adjustment.decidedBy())
                .param("decidedAt", utc(adjustment.decidedAt()))
                .param("rejectionReason", adjustment.rejectionReason())
                .param("journalEntryId", adjustment.journalEntryId())
                .update();
    }

    @Override
    public void update(Adjustment adjustment) {
        jdbcClient.sql("""
                        UPDATE adjustments SET status = :status, decided_by = :decidedBy, decided_at = :decidedAt,
                               rejection_reason = :rejectionReason, journal_entry_id = :journalEntryId
                        WHERE id = :id""")
                .param("id", adjustment.id())
                .param("status", adjustment.status().name())
                .param("decidedBy", adjustment.decidedBy())
                .param("decidedAt", utc(adjustment.decidedAt()))
                .param("rejectionReason", adjustment.rejectionReason())
                .param("journalEntryId", adjustment.journalEntryId())
                .update();
    }

    @Override
    public Optional<Adjustment> findById(UUID id) {
        return one("SELECT %s FROM adjustments WHERE id = :id".formatted(COLUMNS), id);
    }

    @Override
    public Optional<Adjustment> findByIdForUpdate(UUID id) {
        return one("SELECT %s FROM adjustments WHERE id = :id FOR UPDATE".formatted(COLUMNS), id);
    }

    @Override
    public AdjustmentSlice page(AdjustmentStatus status, UUID ticketId, int page, int size) {
        // One more than asked tells whether another page follows
        List<Adjustment> rows = jdbcClient.sql("""
                        SELECT %s FROM adjustments
                        WHERE (CAST(:status AS text) IS NULL OR status = :status)
                          AND (CAST(:ticketId AS uuid) IS NULL OR ticket_id = :ticketId)
                        ORDER BY CASE WHEN status = 'PENDING_APPROVAL' THEN requested_at END ASC,
                                 requested_at DESC, id
                        LIMIT :limit OFFSET :offset""".formatted(COLUMNS))
                .param("status", status == null ? null : status.name())
                .param("ticketId", ticketId)
                .param("limit", size + 1)
                .param("offset", (long) page * size)
                .query((rs, rowNum) -> adjustment(rs))
                .list();
        return new AdjustmentSlice(rows.subList(0, Math.min(size, rows.size())), rows.size() > size);
    }

    @Override
    public long countPending() {
        return jdbcClient.sql("SELECT count(*) FROM adjustments WHERE status = 'PENDING_APPROVAL'")
                .query(Long.class).single();
    }

    private Optional<Adjustment> one(String sql, UUID id) {
        return jdbcClient.sql(sql).param("id", id).query((rs, rowNum) -> adjustment(rs)).optional();
    }

    private static Adjustment adjustment(ResultSet rs) throws SQLException {
        return Adjustment.restore(rs.getObject("id", UUID.class), rs.getObject("wallet_id", UUID.class),
                rs.getObject("user_id", UUID.class), AdjustmentDirection.valueOf(rs.getString("direction")),
                Money.ofMinor(rs.getLong("amount_minor"), rs.getString("currency")), rs.getString("reason"),
                rs.getObject("ticket_id", UUID.class), rs.getObject("requested_by", UUID.class),
                instant(rs, "requested_at"), AdjustmentStatus.valueOf(rs.getString("status")),
                rs.getObject("decided_by", UUID.class), instant(rs, "decided_at"), rs.getString("rejection_reason"),
                rs.getObject("journal_entry_id", UUID.class));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
