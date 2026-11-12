package com.irvinsegura.wallet.support.infrastructure.persistence;

import com.irvinsegura.wallet.support.domain.model.CasePriority;
import com.irvinsegura.wallet.support.domain.model.CaseResolution;
import com.irvinsegura.wallet.support.domain.model.ConversationStatus;
import com.irvinsegura.wallet.support.domain.model.SupportConversation;
import com.irvinsegura.wallet.support.domain.port.SupportConversationRepository;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Plain JDBC: "start one unless there is one" needs ON CONFLICT, the queue needs row locks in order, and the lists read
 * from the messages too.
 */
@Repository
class SupportConversationJdbcAdapter implements SupportConversationRepository {

    private static final String COLUMNS = """
            id, user_id, status, agent_id, created_at, escalated_at, assigned_at, resolved_at, survey_score,
            survey_comment, surveyed_at, priority, resolution""";
    private static final String ROW_COLUMNS = COLUMNS + """
            , last_message_at,
            (SELECT m.text FROM support_messages m WHERE m.conversation_id = support_conversations.id
             AND m.text IS NOT NULL ORDER BY m.seq DESC LIMIT 1) AS last_text""";
    // Starting another conversation is passing on the survey of the one before
    private static final String AWAITING_SURVEY = """
            SELECT %s FROM support_conversations c
            WHERE user_id = :userId AND status = 'RESOLVED' AND agent_id IS NOT NULL AND survey_score IS NULL
              AND NOT EXISTS (SELECT 1 FROM support_conversations later
                              WHERE later.user_id = c.user_id AND later.created_at > c.resolved_at)
            ORDER BY resolved_at DESC LIMIT 1""".formatted(COLUMNS);
    private static final String OPEN = "SELECT %s FROM support_conversations WHERE user_id = :userId AND status <> 'RESOLVED'"
            .formatted(COLUMNS);
    // The last thing said in each case that counts for knowing who is waiting on whom: what people wrote, and the
    // warning that it will close. Other events, such as a ticket opened, don't change that
    private static final String LAST_WORD = """
            SELECT c.id FROM support_conversations c
            CROSS JOIN LATERAL (SELECT m.sender, m.event, m.created_at FROM support_messages m
                                WHERE m.conversation_id = c.id AND (m.sender <> 'SYSTEM' OR m.event = 'INACTIVITY_WARNING')
                                ORDER BY m.seq DESC LIMIT 1) last
            WHERE c.status = 'ASSIGNED' AND last.created_at >= c.assigned_at AND last.created_at < :before AND %s
            ORDER BY c.assigned_at""";
    // Any fixed number will do, as long as nothing else takes it; the letters "SUPPORT" read as hexadecimal digits
    private static final long ASSIGNMENTS_LOCK = 0x5355_5050_4F52_54L;

    private final JdbcClient jdbcClient;

    SupportConversationJdbcAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public SupportConversation openForUpdate(UUID userId, Instant at) {
        SupportConversation fresh = SupportConversation.start(userId, at);
        jdbcClient.sql("""
                        INSERT INTO support_conversations (id, user_id, status, created_at, last_message_at)
                        VALUES (:id, :userId, :status, :at, :at)
                        ON CONFLICT (user_id) WHERE status <> 'RESOLVED' DO NOTHING""")
                .param("id", fresh.id())
                .param("userId", userId)
                .param("status", fresh.status().name())
                .param("at", utc(at))
                .update();
        return findOpenForUpdate(userId).orElseThrow();
    }

    @Override
    public Optional<SupportConversation> findOpenForUpdate(UUID userId) {
        return one(OPEN + " FOR UPDATE", "userId", userId);
    }

    @Override
    public Optional<SupportConversation> findOpen(UUID userId) {
        return one(OPEN, "userId", userId);
    }

    @Override
    public Optional<SupportConversation> findAwaitingSurvey(UUID userId) {
        return one(AWAITING_SURVEY, "userId", userId);
    }

    @Override
    public Optional<SupportConversation> findAwaitingSurveyForUpdate(UUID userId) {
        return one(AWAITING_SURVEY + " FOR UPDATE", "userId", userId);
    }

    @Override
    public Optional<SupportConversation> findById(UUID id) {
        return one("SELECT " + COLUMNS + " FROM support_conversations WHERE id = :id", "id", id);
    }

    @Override
    public Optional<SupportConversation> findByIdForUpdate(UUID id) {
        return one("SELECT " + COLUMNS + " FROM support_conversations WHERE id = :id FOR UPDATE", "id", id);
    }

    @Override
    public void save(SupportConversation conversation) {
        jdbcClient.sql("""
                        UPDATE support_conversations
                        SET status = :status, agent_id = :agentId, escalated_at = :escalatedAt, assigned_at = :assignedAt,
                            resolved_at = :resolvedAt, survey_score = :score, survey_comment = :comment,
                            surveyed_at = :surveyedAt, priority = :priority, resolution = :resolution
                        WHERE id = :id""")
                .param("status", conversation.status().name())
                .param("agentId", conversation.agentId(), java.sql.Types.OTHER)
                .param("escalatedAt", utc(conversation.escalatedAt()), java.sql.Types.TIMESTAMP_WITH_TIMEZONE)
                .param("assignedAt", utc(conversation.assignedAt()), java.sql.Types.TIMESTAMP_WITH_TIMEZONE)
                .param("resolvedAt", utc(conversation.resolvedAt()), java.sql.Types.TIMESTAMP_WITH_TIMEZONE)
                .param("score", conversation.surveyScore(), java.sql.Types.SMALLINT)
                .param("comment", conversation.surveyComment(), java.sql.Types.VARCHAR)
                .param("surveyedAt", utc(conversation.surveyedAt()), java.sql.Types.TIMESTAMP_WITH_TIMEZONE)
                .param("priority", conversation.priority().name())
                .param("resolution", conversation.resolution() == null ? null : conversation.resolution().name(),
                        java.sql.Types.VARCHAR)
                .param("id", conversation.id())
                .update();
    }

    @Override
    public void lockAssignments() {
        jdbcClient.sql("SELECT pg_advisory_xact_lock(:key)").param("key", ASSIGNMENTS_LOCK).query().listOfRows();
    }

    @Override
    public List<SupportConversation> findQueuedForUpdate(int limit) {
        return jdbcClient.sql("""
                        SELECT %s FROM support_conversations WHERE status = 'QUEUED'
                        ORDER BY CASE priority WHEN 'HIGH' THEN 0 ELSE 1 END, escalated_at, id
                        LIMIT :limit FOR UPDATE""".formatted(COLUMNS))
                .param("limit", limit)
                .query(SupportConversationJdbcAdapter::conversation)
                .list();
    }

    @Override
    public Set<UUID> findBusy(Collection<UUID> agents) {
        if (agents.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(jdbcClient.sql(
                        "SELECT agent_id FROM support_conversations WHERE status = 'ASSIGNED' AND agent_id IN (:agents)")
                .param("agents", agents)
                .query((rs, rowNum) -> rs.getObject("agent_id", UUID.class))
                .list());
    }

    @Override
    public Map<UUID, Instant> findLastAssignments(Collection<UUID> agents) {
        if (agents.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Instant> last = new HashMap<>();
        jdbcClient.sql("""
                        SELECT agent_id, max(assigned_at) AS last_assigned FROM support_conversations
                        WHERE agent_id IN (:agents) GROUP BY agent_id""")
                .param("agents", agents)
                .query((rs, rowNum) -> last.put(rs.getObject("agent_id", UUID.class), instant(rs, "last_assigned")))
                .list();
        return last;
    }

    @Override
    public Optional<SupportConversation> findAssignedToForUpdate(UUID agentId) {
        return one("SELECT " + COLUMNS + " FROM support_conversations WHERE agent_id = :agentId AND status = 'ASSIGNED' FOR UPDATE",
                "agentId", agentId);
    }

    @Override
    public Optional<SupportConversation> findAssignedTo(UUID agentId) {
        return one("SELECT " + COLUMNS + " FROM support_conversations WHERE agent_id = :agentId AND status = 'ASSIGNED'",
                "agentId", agentId);
    }

    @Override
    public long countAhead(SupportConversation conversation) {
        return jdbcClient.sql("""
                        SELECT count(*) FROM support_conversations WHERE status = 'QUEUED'
                        AND ((priority = :priority AND escalated_at < :at) OR (:normal AND priority = 'HIGH'))""")
                .param("priority", conversation.priority().name())
                .param("at", utc(conversation.escalatedAt()))
                .param("normal", conversation.priority() == CasePriority.NORMAL)
                .query(Long.class)
                .single();
    }

    @Override
    public ConversationSlice findByAgent(UUID agentId, CaseSearch search, int page, int size) {
        if (findsNothing(search)) {
            return new ConversationSlice(List.of(), false);
        }
        return slice(bind(jdbcClient.sql("""
                        SELECT %s FROM support_conversations WHERE agent_id = :agentId%s
                        ORDER BY CASE status WHEN 'ASSIGNED' THEN 0 ELSE 1 END, last_message_at DESC, id
                        LIMIT :limit OFFSET :offset""".formatted(ROW_COLUMNS, filters(search)))
                .param("agentId", agentId), search), page, size);
    }

    @Override
    public ConversationSlice findEscalated(CaseSearch search, int page, int size) {
        if (findsNothing(search)) {
            return new ConversationSlice(List.of(), false);
        }
        return slice(bind(jdbcClient.sql("""
                SELECT %s FROM support_conversations WHERE escalated_at IS NOT NULL%s
                ORDER BY last_message_at DESC, id
                LIMIT :limit OFFSET :offset""".formatted(ROW_COLUMNS, filters(search))), search), page, size);
    }

    @Override
    public List<ConversationRow> findEscalatedByUser(UUID userId, UUID except, int limit) {
        return jdbcClient.sql("""
                        SELECT %s FROM support_conversations
                        WHERE user_id = :userId AND id <> :except AND escalated_at IS NOT NULL
                        ORDER BY last_message_at DESC, id LIMIT :limit""".formatted(ROW_COLUMNS))
                .param("userId", userId)
                .param("except", except)
                .param("limit", limit)
                .query(SupportConversationJdbcAdapter::row)
                .list();
    }

    @Override
    public QueueState queueState() {
        return jdbcClient.sql("""
                        SELECT count(*) AS queued, count(*) FILTER (WHERE priority = 'HIGH') AS high,
                               min(escalated_at) AS oldest
                        FROM support_conversations WHERE status = 'QUEUED'""")
                .query((rs, rowNum) -> new QueueState(rs.getLong("queued"), rs.getLong("high"), instant(rs, "oldest")))
                .single();
    }

    @Override
    public List<UUID> findAwaitingCustomer(Instant before) {
        return lastWord("last.sender = 'AGENT'", before);
    }

    @Override
    public List<UUID> findWarnedBefore(Instant before) {
        return lastWord("last.event = 'INACTIVITY_WARNING'", before);
    }

    private List<UUID> lastWord(String condition, Instant before) {
        return jdbcClient.sql(LAST_WORD.formatted(condition))
                .param("before", utc(before))
                .query((rs, rowNum) -> rs.getObject("id", UUID.class))
                .list();
    }

    @Override
    public List<AgentStats> agentStats(Collection<UUID> agents, Instant since) {
        if (agents.isEmpty()) {
            return List.of();
        }
        // The first answer counts from when the case reached them, which a transfer moves to then
        return jdbcClient.sql("""
                        SELECT c.agent_id, count(*) AS attended,
                               count(*) FILTER (WHERE c.status = 'RESOLVED') AS resolved,
                               avg(EXTRACT(EPOCH FROM (f.first_at - c.assigned_at))) AS first_response,
                               avg(EXTRACT(EPOCH FROM (c.resolved_at - c.assigned_at)))
                                   FILTER (WHERE c.status = 'RESOLVED') AS handle,
                               count(c.survey_score) AS surveys, avg(c.survey_score) AS average_score
                        FROM support_conversations c
                        LEFT JOIN LATERAL (SELECT min(m.created_at) AS first_at FROM support_messages m
                                           WHERE m.conversation_id = c.id AND m.sender = 'AGENT'
                                             AND m.sender_id = c.agent_id AND m.created_at >= c.assigned_at) f ON TRUE
                        WHERE c.agent_id IN (:agents) AND c.assigned_at >= :since
                        GROUP BY c.agent_id""")
                .param("agents", agents)
                .param("since", utc(since))
                .query((rs, rowNum) -> new AgentStats(rs.getObject("agent_id", UUID.class), rs.getLong("attended"),
                        rs.getLong("resolved"), decimal(rs, "first_response"), decimal(rs, "handle"),
                        rs.getLong("surveys"), decimal(rs, "average_score")))
                .list();
    }

    // Typed text that matches no customer and can't be a folio finds nothing, rather than everything
    private static boolean findsNothing(CaseSearch search) {
        return search.byText() && search.customers().isEmpty() && search.folio() == null;
    }

    private static String filters(CaseSearch search) {
        StringBuilder sql = new StringBuilder();
        if (search.status() != null) {
            sql.append(" AND status = :status");
        }
        if (search.byText()) {
            List<String> matches = new ArrayList<>();
            if (!search.customers().isEmpty()) {
                matches.add("user_id IN (:customers)");
            }
            if (search.folio() != null) {
                matches.add("CAST(id AS text) LIKE :folio");
            }
            sql.append(" AND (").append(String.join(" OR ", matches)).append(')');
        }
        return sql.toString();
    }

    private static JdbcClient.StatementSpec bind(JdbcClient.StatementSpec query, CaseSearch search) {
        JdbcClient.StatementSpec bound = query;
        if (search.status() != null) {
            bound = bound.param("status", search.status().name());
        }
        if (search.byText() && !search.customers().isEmpty()) {
            bound = bound.param("customers", search.customers());
        }
        if (search.byText() && search.folio() != null) {
            bound = bound.param("folio", search.folio() + "%");
        }
        return bound;
    }

    @Override
    public SupportCounts count() {
        SupportCounts counts = jdbcClient.sql("""
                        SELECT count(*) FILTER (WHERE status = 'BOT') AS with_bot,
                               count(*) FILTER (WHERE status = 'QUEUED') AS queued,
                               count(*) FILTER (WHERE status = 'ASSIGNED') AS assigned,
                               count(*) FILTER (WHERE status = 'RESOLVED' AND escalated_at IS NOT NULL) AS resolved,
                               count(survey_score) AS surveys,
                               avg(survey_score) AS average_score,
                               count(*) FILTER (WHERE status = 'QUEUED' AND priority = 'HIGH') AS queued_high,
                               min(escalated_at) FILTER (WHERE status = 'QUEUED') AS oldest_queued
                        FROM support_conversations""")
                .query((rs, rowNum) -> new SupportCounts(rs.getLong("with_bot"), rs.getLong("queued"),
                        rs.getLong("assigned"), rs.getLong("resolved"), rs.getLong("surveys"),
                        decimal(rs, "average_score"), rs.getLong("queued_high"), instant(rs, "oldest_queued"),
                        Map.of()))
                .single();
        return new SupportCounts(counts.withBot(), counts.queued(), counts.assigned(), counts.resolved(),
                counts.surveys(), counts.averageScore(), counts.queuedHighPriority(), counts.oldestQueuedAt(),
                resolutions());
    }

    private Map<CaseResolution, Long> resolutions() {
        Map<CaseResolution, Long> counts = new EnumMap<>(CaseResolution.class);
        jdbcClient.sql("""
                        SELECT resolution, count(*) AS total FROM support_conversations
                        WHERE resolution IS NOT NULL GROUP BY resolution""")
                .query((rs, rowNum) -> counts.put(CaseResolution.valueOf(rs.getString("resolution")), rs.getLong("total")))
                .list();
        return counts;
    }

    private static Double decimal(ResultSet rs, String column) throws SQLException {
        BigDecimal value = rs.getBigDecimal(column);
        return value == null ? null : value.doubleValue();
    }

    private Optional<SupportConversation> one(String sql, String parameter, UUID value) {
        return jdbcClient.sql(sql).param(parameter, value).query(SupportConversationJdbcAdapter::conversation).optional();
    }

    // One more than asked for, to learn whether there is a next page without counting them all
    private static ConversationSlice slice(JdbcClient.StatementSpec query, int page, int size) {
        List<ConversationRow> rows = query.param("limit", size + 1)
                .param("offset", (long) page * size)
                .query(SupportConversationJdbcAdapter::row)
                .list();
        return new ConversationSlice(rows.stream().limit(size).toList(), rows.size() > size);
    }

    private static ConversationRow row(ResultSet rs, int rowNum) throws SQLException {
        return new ConversationRow(conversation(rs, rowNum), rs.getString("last_text"), instant(rs, "last_message_at"));
    }

    private static SupportConversation conversation(ResultSet rs, int rowNum) throws SQLException {
        String resolution = rs.getString("resolution");
        return SupportConversation.restore(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
                instant(rs, "created_at"), ConversationStatus.valueOf(rs.getString("status")),
                rs.getObject("agent_id", UUID.class), instant(rs, "escalated_at"), instant(rs, "assigned_at"),
                instant(rs, "resolved_at"), rs.getObject("survey_score", Integer.class), rs.getString("survey_comment"),
                instant(rs, "surveyed_at"), CasePriority.valueOf(rs.getString("priority")),
                resolution == null ? null : CaseResolution.valueOf(resolution));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
