package com.irvinsegura.wallet.support.infrastructure.persistence;

import com.irvinsegura.wallet.shared.domain.Language;
import com.irvinsegura.wallet.support.domain.model.BotFeedback;
import com.irvinsegura.wallet.support.domain.model.BotMissKind;
import com.irvinsegura.wallet.support.domain.model.BotQuestion;
import com.irvinsegura.wallet.support.domain.port.SupportBotQuestionRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class SupportBotQuestionJdbcAdapter implements SupportBotQuestionRepository {

    private final JdbcClient jdbcClient;

    SupportBotQuestionJdbcAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public void add(BotQuestion question) {
        jdbcClient.sql("""
                        INSERT INTO support_bot_questions (message_id, conversation_id, language, outcome, topic, created_at)
                        VALUES (:messageId, :conversationId, :language, :outcome, :topic, :at)""")
                .param("messageId", question.messageId())
                .param("conversationId", question.conversationId())
                .param("language", question.language().tag())
                .param("outcome", question.outcome().name())
                .param("topic", question.topic(), Types.VARCHAR)
                .param("at", question.createdAt().atOffset(ZoneOffset.UTC))
                .update();
    }

    @Override
    public void recordFeedback(UUID conversationId, BotFeedback feedback) {
        jdbcClient.sql("""
                        UPDATE support_bot_questions SET feedback = :feedback
                        WHERE message_id = (SELECT q.message_id
                                            FROM support_bot_questions q
                                            JOIN support_messages m ON m.id = q.message_id
                                            WHERE q.conversation_id = :conversationId
                                            ORDER BY m.seq DESC
                                            LIMIT 1)
                          AND outcome = 'ANSWERED' AND feedback IS NULL""")
                .param("feedback", feedback.name())
                .param("conversationId", conversationId)
                .update();
    }

    @Override
    public BotSummary summarize(Instant since) {
        return jdbcClient.sql("""
                        SELECT count(*) FILTER (WHERE outcome <> 'SMALL_TALK') AS questions,
                               count(*) FILTER (WHERE outcome = 'ANSWERED') AS answered,
                               count(*) FILTER (WHERE feedback = 'HELPED') AS helped,
                               count(*) FILTER (WHERE feedback = 'NOT_HELPED') AS not_helped,
                               count(*) FILTER (WHERE outcome = 'PASSED_ON') AS passed_on,
                               count(*) FILTER (WHERE outcome = 'SUGGESTED') AS suggested,
                               count(*) FILTER (WHERE outcome = 'ASKED_AGAIN') AS asked_again,
                               count(*) FILTER (WHERE outcome = 'NOT_FOUND') AS not_found
                        FROM support_bot_questions
                        WHERE created_at >= :since""")
                .param("since", since.atOffset(ZoneOffset.UTC))
                .query((rs, rowNum) -> new BotSummary(rs.getLong("questions"), rs.getLong("answered"),
                        rs.getLong("helped"), rs.getLong("not_helped"), rs.getLong("passed_on"),
                        rs.getLong("suggested"), rs.getLong("asked_again"), rs.getLong("not_found")))
                .single();
    }

    // Grouped by how it was written, ignoring case and spaces around it, so the same question shows once with its count
    @Override
    public BotMissSlice findMisses(List<BotMissKind> kinds, Language language, Instant since, int page, int size) {
        List<BotMiss> rows = jdbcClient.sql("""
                        SELECT k.kind, k.language, k.topic, count(*) AS times, max(k.created_at) AS last_asked_at,
                               (array_agg(k.text ORDER BY k.seq DESC))[1] AS question,
                               (array_agg(k.conversation_id ORDER BY k.seq DESC))[1] AS conversation_id
                        FROM (SELECT CASE WHEN q.feedback = 'NOT_HELPED' THEN 'NOT_HELPED' ELSE q.outcome END AS kind,
                                     q.language, q.topic, q.created_at, q.conversation_id, m.text, m.seq
                              FROM support_bot_questions q
                              JOIN support_messages m ON m.id = q.message_id
                              WHERE q.created_at >= :since
                                AND (CAST(:language AS VARCHAR) IS NULL OR q.language = :language)) k
                        WHERE k.kind IN (:kinds)
                        GROUP BY k.kind, k.language, k.topic, lower(btrim(k.text))
                        ORDER BY times DESC, last_asked_at DESC, kind
                        LIMIT :limit OFFSET :offset""")
                .param("since", since.atOffset(ZoneOffset.UTC))
                .param("language", language == null ? null : language.tag(), Types.VARCHAR)
                .param("kinds", kinds.stream().map(Enum::name).toList())
                // One more than asked for, to learn whether there is a next page without counting them all
                .param("limit", size + 1)
                .param("offset", (long) page * size)
                .query(SupportBotQuestionJdbcAdapter::miss)
                .list();
        boolean hasNext = rows.size() > size;
        return new BotMissSlice(hasNext ? rows.subList(0, size) : rows, hasNext);
    }

    private static BotMiss miss(ResultSet rs, int rowNum) throws SQLException {
        return new BotMiss(BotMissKind.valueOf(rs.getString("kind")),
                Language.ofTag(rs.getString("language")).orElse(Language.DEFAULT), rs.getString("question"),
                rs.getString("topic"), rs.getLong("times"),
                rs.getObject("last_asked_at", OffsetDateTime.class).toInstant(),
                rs.getObject("conversation_id", UUID.class));
    }
}
