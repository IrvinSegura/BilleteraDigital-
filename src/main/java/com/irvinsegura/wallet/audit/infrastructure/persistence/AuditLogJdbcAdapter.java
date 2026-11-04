package com.irvinsegura.wallet.audit.infrastructure.persistence;

import com.irvinsegura.wallet.audit.domain.model.AuditEntry;
import com.irvinsegura.wallet.audit.domain.port.AuditLogRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Plain JDBC: the log is append-only, and "insert unless it exists" needs ON CONFLICT, which JPA cannot express.
 */
@Repository
class AuditLogJdbcAdapter implements AuditLogRepository {

    private final JdbcClient jdbcClient;

    AuditLogJdbcAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public void append(AuditEntry entry) {
        jdbcClient.sql("""
                        INSERT INTO audit_events (id, occurred_at, actor_id, action, target_type, target_id, details)
                        VALUES (:id, :occurredAt, :actorId, :action, :targetType, :targetId, :details)
                        ON CONFLICT (id) DO NOTHING""")
                .param("id", entry.id())
                .param("occurredAt", OffsetDateTime.ofInstant(entry.occurredAt(), ZoneOffset.UTC))
                .param("actorId", entry.actorId())
                .param("action", entry.action())
                .param("targetType", entry.targetType())
                .param("targetId", entry.targetId())
                .param("details", entry.details())
                .update();
    }

    @Override
    public AuditSlice find(UUID actorId, UUID targetId, String action, Instant from, Instant to, int page, int size) {
        // Casting the parameters lets PostgreSQL type a null filter, which it cannot infer on its own
        List<AuditEntry> rows = jdbcClient.sql("""
                        SELECT id, occurred_at, actor_id, action, target_type, target_id, details
                        FROM audit_events
                        WHERE (CAST(:actorId AS UUID) IS NULL OR actor_id = :actorId)
                          AND (CAST(:targetId AS UUID) IS NULL OR target_id = :targetId)
                          AND (CAST(:action AS VARCHAR) IS NULL OR action = :action)
                          AND (CAST(:from AS TIMESTAMPTZ) IS NULL OR occurred_at >= :from)
                          AND (CAST(:to AS TIMESTAMPTZ) IS NULL OR occurred_at < :to)
                        ORDER BY occurred_at DESC, id
                        LIMIT :limit OFFSET :offset""")
                .param("actorId", actorId)
                .param("targetId", targetId)
                .param("action", action)
                .param("from", from == null ? null : OffsetDateTime.ofInstant(from, ZoneOffset.UTC))
                .param("to", to == null ? null : OffsetDateTime.ofInstant(to, ZoneOffset.UTC))
                .param("limit", size + 1)
                .param("offset", (long) page * size)
                .query(AuditLogJdbcAdapter::mapRow)
                .list();
        boolean hasNext = rows.size() > size;
        return new AuditSlice(hasNext ? rows.subList(0, size) : rows, hasNext);
    }

    private static AuditEntry mapRow(ResultSet rs, int rowNum) throws SQLException {
        Instant occurredAt = rs.getObject("occurred_at", OffsetDateTime.class).toInstant();
        return new AuditEntry(rs.getObject("id", UUID.class), occurredAt, rs.getObject("actor_id", UUID.class),
                rs.getString("action"), rs.getString("target_type"), rs.getObject("target_id", UUID.class),
                rs.getString("details"));
    }
}
