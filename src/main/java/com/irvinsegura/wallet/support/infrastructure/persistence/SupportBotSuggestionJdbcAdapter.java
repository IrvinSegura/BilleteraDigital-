package com.irvinsegura.wallet.support.infrastructure.persistence;

import com.irvinsegura.wallet.shared.domain.Language;
import com.irvinsegura.wallet.support.domain.model.BotSuggestion;
import com.irvinsegura.wallet.support.domain.port.SupportBotSuggestionRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class SupportBotSuggestionJdbcAdapter implements SupportBotSuggestionRepository {

    private static final String COLUMNS = """
            id, conversation_id, agent_id, language, question, answer, status, created_at, reviewed_by, reviewed_at""";

    private final JdbcClient jdbcClient;

    SupportBotSuggestionJdbcAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public void insert(BotSuggestion suggestion) {
        jdbcClient.sql("""
                        INSERT INTO support_bot_suggestions (id, conversation_id, agent_id, language, question, answer,
                                                             status, created_at)
                        VALUES (:id, :conversationId, :agentId, :language, :question, :answer, :status, :createdAt)""")
                .param("id", suggestion.id())
                .param("conversationId", suggestion.conversationId(), Types.OTHER)
                .param("agentId", suggestion.agentId())
                .param("language", suggestion.language().tag())
                .param("question", suggestion.question())
                .param("answer", suggestion.answer())
                .param("status", suggestion.status().name())
                .param("createdAt", utc(suggestion.createdAt()))
                .update();
    }

    @Override
    public void update(BotSuggestion suggestion) {
        jdbcClient.sql("""
                        UPDATE support_bot_suggestions SET status = :status, reviewed_by = :reviewedBy,
                            reviewed_at = :reviewedAt
                        WHERE id = :id""")
                .param("status", suggestion.status().name())
                .param("reviewedBy", suggestion.reviewedBy(), Types.OTHER)
                .param("reviewedAt", utc(suggestion.reviewedAt()), Types.TIMESTAMP_WITH_TIMEZONE)
                .param("id", suggestion.id())
                .update();
    }

    @Override
    public Optional<BotSuggestion> findByIdForUpdate(UUID id) {
        return jdbcClient.sql("SELECT " + COLUMNS + " FROM support_bot_suggestions WHERE id = :id FOR UPDATE")
                .param("id", id)
                .query(SupportBotSuggestionJdbcAdapter::suggestion)
                .optional();
    }

    @Override
    public SuggestionSlice page(BotSuggestion.Status status, int page, int size) {
        String where = status == null ? "" : "WHERE status = :status ";
        String order = status == BotSuggestion.Status.OPEN ? "created_at, id" : "created_at DESC, id";
        JdbcClient.StatementSpec query = jdbcClient.sql("SELECT " + COLUMNS + " FROM support_bot_suggestions "
                        + where + "ORDER BY " + order + " LIMIT :limit OFFSET :offset")
                .param("limit", size + 1)
                .param("offset", (long) page * size);
        if (status != null) {
            query = query.param("status", status.name());
        }
        List<BotSuggestion> rows = query.query(SupportBotSuggestionJdbcAdapter::suggestion).list();
        return new SuggestionSlice(rows.stream().limit(size).toList(), rows.size() > size);
    }

    @Override
    public long countOpen() {
        return jdbcClient.sql("SELECT count(*) FROM support_bot_suggestions WHERE status = 'OPEN'")
                .query(Long.class)
                .single();
    }

    private static BotSuggestion suggestion(ResultSet rs, int rowNum) throws SQLException {
        return BotSuggestion.restore(rs.getObject("id", UUID.class), rs.getObject("conversation_id", UUID.class),
                rs.getObject("agent_id", UUID.class), Language.ofTag(rs.getString("language")).orElseThrow(),
                rs.getString("question"), rs.getString("answer"), instant(rs, "created_at"),
                BotSuggestion.Status.valueOf(rs.getString("status")), rs.getObject("reviewed_by", UUID.class),
                instant(rs, "reviewed_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
