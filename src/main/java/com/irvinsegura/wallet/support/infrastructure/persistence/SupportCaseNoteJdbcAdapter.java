package com.irvinsegura.wallet.support.infrastructure.persistence;

import com.irvinsegura.wallet.support.domain.model.CaseNote;
import com.irvinsegura.wallet.support.domain.port.SupportCaseNoteRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class SupportCaseNoteJdbcAdapter implements SupportCaseNoteRepository {

    private final JdbcClient jdbcClient;

    SupportCaseNoteJdbcAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public void add(CaseNote note) {
        jdbcClient.sql("""
                        INSERT INTO support_case_notes (id, conversation_id, author_id, text, created_at)
                        VALUES (:id, :conversationId, :authorId, :text, :createdAt)""")
                .param("id", note.id())
                .param("conversationId", note.conversationId())
                .param("authorId", note.authorId())
                .param("text", note.text())
                .param("createdAt", note.createdAt().atOffset(ZoneOffset.UTC))
                .update();
    }

    @Override
    public List<CaseNote> findByConversation(UUID conversationId) {
        return jdbcClient.sql("""
                        SELECT id, conversation_id, author_id, text, created_at FROM support_case_notes
                        WHERE conversation_id = :conversationId ORDER BY created_at, id""")
                .param("conversationId", conversationId)
                .query((rs, rowNum) -> new CaseNote(rs.getObject("id", UUID.class),
                        rs.getObject("conversation_id", UUID.class), rs.getObject("author_id", UUID.class),
                        rs.getString("text"), rs.getObject("created_at", OffsetDateTime.class).toInstant()))
                .list();
    }
}
