package com.irvinsegura.wallet.notifications.infrastructure.persistence;

import com.irvinsegura.wallet.notifications.domain.model.Notification;
import com.irvinsegura.wallet.notifications.domain.model.NotificationType;
import com.irvinsegura.wallet.notifications.domain.port.NotificationRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Plain JDBC, like the audit log: "insert unless it exists" needs ON CONFLICT, and the bulk updates and deletes are
 * single statements.
 */
@Repository
class NotificationJdbcAdapter implements NotificationRepository {

    private static final String COLUMNS = "id, user_id, type, title, body, reference_id, created_at, read_at, params";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<Map<String, String>> PARAMS = new TypeReference<>() {
    };

    private final JdbcClient jdbcClient;

    NotificationJdbcAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public boolean add(Notification notification) {
        return jdbcClient.sql("""
                        INSERT INTO notifications (id, user_id, type, title, body, reference_id, created_at, read_at, params)
                        VALUES (:id, :userId, :type, :title, :body, :referenceId, :createdAt, :readAt,
                                CAST(:params AS jsonb))
                        ON CONFLICT (id) DO NOTHING""")
                .param("id", notification.id())
                .param("userId", notification.userId())
                .param("type", notification.type().name())
                .param("title", notification.title())
                .param("body", notification.body())
                .param("referenceId", notification.referenceId())
                .param("createdAt", timestamp(notification.createdAt()))
                .param("readAt", timestamp(notification.readAt()))
                .param("params", JSON.writeValueAsString(notification.params()))
                .update() == 1;
    }

    @Override
    public Optional<Notification> findById(UUID id) {
        return jdbcClient.sql("SELECT " + COLUMNS + " FROM notifications WHERE id = :id")
                .param("id", id)
                .query(NotificationJdbcAdapter::mapRow)
                .optional();
    }

    @Override
    public NotificationSlice findByUser(UUID userId, boolean unreadOnly, int page, int size) {
        List<Notification> rows = jdbcClient.sql("SELECT " + COLUMNS + """
                         FROM notifications
                        WHERE user_id = :userId
                          AND (:unreadOnly = FALSE OR read_at IS NULL)
                        ORDER BY created_at DESC, id
                        LIMIT :limit OFFSET :offset""")
                .param("userId", userId)
                .param("unreadOnly", unreadOnly)
                .param("limit", size + 1)
                .param("offset", (long) page * size)
                .query(NotificationJdbcAdapter::mapRow)
                .list();
        boolean hasNext = rows.size() > size;
        return new NotificationSlice(hasNext ? rows.subList(0, size) : rows, hasNext);
    }

    @Override
    public long countUnread(UUID userId) {
        return jdbcClient.sql("SELECT count(*) FROM notifications WHERE user_id = :userId AND read_at IS NULL")
                .param("userId", userId)
                .query(Long.class)
                .single();
    }

    @Override
    public boolean markRead(UUID id, UUID userId, Instant readAt) {
        return jdbcClient.sql("""
                        UPDATE notifications SET read_at = COALESCE(read_at, :readAt)
                        WHERE id = :id AND user_id = :userId""")
                .param("readAt", timestamp(readAt))
                .param("id", id)
                .param("userId", userId)
                .update() == 1;
    }

    @Override
    public int markAllRead(UUID userId, Instant readAt) {
        return jdbcClient.sql("UPDATE notifications SET read_at = :readAt WHERE user_id = :userId AND read_at IS NULL")
                .param("readAt", timestamp(readAt))
                .param("userId", userId)
                .update();
    }

    @Override
    public boolean delete(UUID id, UUID userId) {
        return jdbcClient.sql("DELETE FROM notifications WHERE id = :id AND user_id = :userId")
                .param("id", id)
                .param("userId", userId)
                .update() == 1;
    }

    @Override
    public int deleteRead(UUID userId) {
        return jdbcClient.sql("DELETE FROM notifications WHERE user_id = :userId AND read_at IS NOT NULL")
                .param("userId", userId)
                .update();
    }

    @Override
    public int deleteCreatedBefore(Instant cutoff) {
        return jdbcClient.sql("DELETE FROM notifications WHERE created_at < :cutoff")
                .param("cutoff", timestamp(cutoff))
                .update();
    }

    private static OffsetDateTime timestamp(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Notification mapRow(ResultSet rs, int rowNum) throws SQLException {
        OffsetDateTime readAt = rs.getObject("read_at", OffsetDateTime.class);
        return new Notification(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
                NotificationType.valueOf(rs.getString("type")), rs.getString("title"), rs.getString("body"),
                rs.getObject("reference_id", UUID.class), rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                readAt == null ? null : readAt.toInstant(), JSON.readValue(rs.getString("params"), PARAMS));
    }
}
