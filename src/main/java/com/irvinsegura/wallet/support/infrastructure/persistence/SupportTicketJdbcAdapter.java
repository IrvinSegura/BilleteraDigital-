package com.irvinsegura.wallet.support.infrastructure.persistence;

import com.irvinsegura.wallet.support.domain.model.SupportTicket;
import com.irvinsegura.wallet.support.domain.model.TicketCategory;
import com.irvinsegura.wallet.support.domain.model.TicketStatus;
import com.irvinsegura.wallet.support.domain.port.SupportTicketRepository;
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
 * Plain JDBC: "one open ticket per operation", and per customer and subject for the rest, are partial unique indexes,
 * kept with ON CONFLICT.
 */
@Repository
class SupportTicketJdbcAdapter implements SupportTicketRepository {

    private static final String COLUMNS = """
            id, conversation_id, user_id, opened_by, category, operation_kind, operation_id, description, status, resolution,
            resolved_by, created_at, resolved_at""";

    private final JdbcClient jdbcClient;

    SupportTicketJdbcAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public boolean insert(SupportTicket ticket) {
        return jdbcClient.sql("""
                        INSERT INTO support_tickets (id, conversation_id, user_id, opened_by, category,
                                                     operation_kind, operation_id, description, status, created_at)
                        VALUES (:id, :conversationId, :userId, :openedBy, :category, :operationKind, :operationId,
                                :description, :status, :createdAt)
                        ON CONFLICT DO NOTHING""")
                .param("id", ticket.id())
                .param("conversationId", ticket.conversationId())
                .param("userId", ticket.userId())
                .param("openedBy", ticket.openedBy())
                .param("category", ticket.category().name())
                .param("operationKind", ticket.operationKind())
                .param("operationId", ticket.operationId())
                .param("description", ticket.description())
                .param("status", ticket.status().name())
                .param("createdAt", utc(ticket.createdAt()))
                .update() == 1;
    }

    @Override
    public void update(SupportTicket ticket) {
        jdbcClient.sql("""
                        UPDATE support_tickets SET status = :status, resolution = :resolution,
                               resolved_by = :resolvedBy, resolved_at = :resolvedAt
                        WHERE id = :id""")
                .param("id", ticket.id())
                .param("status", ticket.status().name())
                .param("resolution", ticket.resolution())
                .param("resolvedBy", ticket.resolvedBy())
                .param("resolvedAt", utc(ticket.resolvedAt()))
                .update();
    }

    @Override
    public Optional<SupportTicket> findById(UUID id) {
        return one("SELECT %s FROM support_tickets WHERE id = :id".formatted(COLUMNS), id);
    }

    @Override
    public Optional<SupportTicket> findByIdForUpdate(UUID id) {
        return one("SELECT %s FROM support_tickets WHERE id = :id FOR UPDATE".formatted(COLUMNS), id);
    }

    @Override
    public List<SupportTicket> findByConversation(UUID conversationId) {
        return jdbcClient.sql("SELECT %s FROM support_tickets WHERE conversation_id = :conversationId ORDER BY created_at"
                        .formatted(COLUMNS))
                .param("conversationId", conversationId)
                .query((rs, rowNum) -> ticket(rs))
                .list();
    }

    @Override
    public TicketSlice page(TicketStatus status, int page, int size) {
        return slice(null, status, page, size);
    }

    @Override
    public TicketSlice pageOpenedBy(UUID agentId, TicketStatus status, int page, int size) {
        return slice(agentId, status, page, size);
    }

    @Override
    public long countOpenOpenedBy(UUID agentId) {
        return jdbcClient.sql("SELECT count(*) FROM support_tickets WHERE status = 'OPEN' AND opened_by = :agentId")
                .param("agentId", agentId)
                .query(Long.class)
                .single();
    }

    // Open ones the oldest first, as they have waited longest, then the rest the newest first
    private TicketSlice slice(UUID openedBy, TicketStatus status, int page, int size) {
        // One more than asked tells whether another page follows
        List<SupportTicket> rows = jdbcClient.sql("""
                        SELECT %s FROM support_tickets
                        WHERE (CAST(:status AS text) IS NULL OR status = :status)
                          AND (CAST(:openedBy AS uuid) IS NULL OR opened_by = :openedBy)
                        ORDER BY CASE WHEN status = 'OPEN' THEN created_at END ASC,
                                 created_at DESC, id
                        LIMIT :limit OFFSET :offset""".formatted(COLUMNS))
                .param("status", status == null ? null : status.name())
                .param("openedBy", openedBy)
                .param("limit", size + 1)
                .param("offset", (long) page * size)
                .query((rs, rowNum) -> ticket(rs))
                .list();
        return new TicketSlice(rows.subList(0, Math.min(size, rows.size())), rows.size() > size);
    }

    @Override
    public long countOpen() {
        return jdbcClient.sql("SELECT count(*) FROM support_tickets WHERE status = 'OPEN'").query(Long.class).single();
    }

    private Optional<SupportTicket> one(String sql, UUID id) {
        return jdbcClient.sql(sql).param("id", id).query((rs, rowNum) -> ticket(rs)).optional();
    }

    private static SupportTicket ticket(ResultSet rs) throws SQLException {
        return SupportTicket.restore(rs.getObject("id", UUID.class), rs.getObject("conversation_id", UUID.class),
                rs.getObject("user_id", UUID.class), rs.getObject("opened_by", UUID.class),
                TicketCategory.valueOf(rs.getString("category")), rs.getString("operation_kind"), rs.getObject("operation_id", UUID.class), rs.getString("description"),
                instant(rs, "created_at"), TicketStatus.valueOf(rs.getString("status")), rs.getString("resolution"),
                rs.getObject("resolved_by", UUID.class), instant(rs, "resolved_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
