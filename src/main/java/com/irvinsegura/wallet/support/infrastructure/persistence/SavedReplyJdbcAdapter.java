package com.irvinsegura.wallet.support.infrastructure.persistence;

import com.irvinsegura.wallet.support.domain.model.SavedReply;
import com.irvinsegura.wallet.support.domain.port.SavedReplyRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Plain JDBC. The limit per agent is checked under a lock on the agent's row, so two saves at once can't pass it.
 */
@Repository
class SavedReplyJdbcAdapter implements SavedReplyRepository {

    private final JdbcClient jdbcClient;

    SavedReplyJdbcAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public boolean insert(SavedReply reply) {
        jdbcClient.sql("SELECT 1 FROM users WHERE id = :agentId FOR UPDATE").param("agentId", reply.agentId())
                .query(Integer.class).optional();
        return jdbcClient.sql("""
                        INSERT INTO support_saved_replies (id, agent_id, title, text, created_at)
                        SELECT :id, :agentId, :title, :text, :createdAt
                        WHERE (SELECT count(*) FROM support_saved_replies WHERE agent_id = :agentId) < :max""")
                .param("id", reply.id())
                .param("agentId", reply.agentId())
                .param("title", reply.title())
                .param("text", reply.text())
                .param("createdAt", reply.createdAt().atOffset(ZoneOffset.UTC))
                .param("max", SavedReply.MAX_PER_AGENT)
                .update() == 1;
    }

    @Override
    public List<SavedReply> findByAgent(UUID agentId) {
        return jdbcClient.sql("""
                        SELECT id, agent_id, title, text, created_at FROM support_saved_replies
                        WHERE agent_id = :agentId ORDER BY lower(title), created_at""")
                .param("agentId", agentId)
                .query((rs, rowNum) -> new SavedReply(rs.getObject("id", UUID.class),
                        rs.getObject("agent_id", UUID.class), rs.getString("title"), rs.getString("text"),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant()))
                .list();
    }

    @Override
    public boolean delete(UUID agentId, UUID replyId) {
        return jdbcClient.sql("DELETE FROM support_saved_replies WHERE id = :id AND agent_id = :agentId")
                .param("id", replyId).param("agentId", agentId).update() == 1;
    }
}
