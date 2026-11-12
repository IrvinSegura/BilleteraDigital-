package com.irvinsegura.wallet.support.infrastructure.persistence;

import com.irvinsegura.wallet.support.domain.model.ConversationEvent;
import com.irvinsegura.wallet.support.domain.model.MessageSender;
import com.irvinsegura.wallet.support.domain.model.SupportAttachment;
import com.irvinsegura.wallet.support.domain.model.SupportMessage;
import com.irvinsegura.wallet.support.domain.port.SupportMessageRepository;
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

/**
 * Plain JDBC, so reading a conversation never loads the bytes of its files: only a download asks for them.
 */
@Repository
class SupportMessageJdbcAdapter implements SupportMessageRepository {

    private static final String SUGGESTION_SEPARATOR = "\n";

    private final JdbcClient jdbcClient;

    SupportMessageJdbcAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<Instant> findLastAt(UUID conversationId, MessageSender sender) {
        return jdbcClient.sql(
                        "SELECT max(created_at) FROM support_messages WHERE conversation_id = :id AND sender = :sender")
                .param("id", conversationId)
                .param("sender", sender.name())
                .query((rs, rowNum) -> rs.getObject(1, OffsetDateTime.class))
                .list().stream()
                .filter(java.util.Objects::nonNull)
                .map(OffsetDateTime::toInstant)
                .findFirst();
    }

    @Override
    public void add(SupportMessage message) {
        OffsetDateTime at = message.createdAt().atOffset(ZoneOffset.UTC);
        jdbcClient.sql("""
                        INSERT INTO support_messages
                            (id, conversation_id, sender, sender_id, text, event, asks_feedback, attachment_id, suggestions, created_at)
                        VALUES (:id, :conversationId, :sender, :senderId, :text, :event, :asksFeedback, :attachmentId,
                                :suggestions, :at)""")
                .param("id", message.id())
                .param("conversationId", message.conversationId())
                .param("sender", message.sender().name())
                .param("senderId", message.senderId(), Types.OTHER)
                .param("text", message.text(), Types.VARCHAR)
                .param("event", message.event() == null ? null : message.event().name(), Types.VARCHAR)
                .param("asksFeedback", message.asksFeedback())
                .param("attachmentId", message.attachment() == null ? null : message.attachment().id(), Types.OTHER)
                .param("suggestions", message.suggestions().isEmpty() ? null
                        : String.join(SUGGESTION_SEPARATOR, message.suggestions()), Types.VARCHAR)
                .param("at", at)
                .update();
        jdbcClient.sql("UPDATE support_conversations SET last_message_at = :at WHERE id = :id")
                .param("at", at)
                .param("id", message.conversationId())
                .update();
    }

    @Override
    public List<SupportMessage> findByConversation(UUID conversationId) {
        return jdbcClient.sql("""
                        SELECT m.id, m.conversation_id, m.sender, m.sender_id, m.text, m.event, m.asks_feedback, m.suggestions,
                               m.created_at,
                               a.id AS attachment_id, a.file_name, a.content_type, a.size_bytes
                        FROM support_messages m
                        LEFT JOIN support_attachments a ON a.id = m.attachment_id
                        WHERE m.conversation_id = :conversationId
                        ORDER BY m.seq""")
                .param("conversationId", conversationId)
                .query(SupportMessageJdbcAdapter::message)
                .list();
    }

    @Override
    public void addAttachment(SupportAttachment attachment, byte[] content, Instant at) {
        jdbcClient.sql("""
                        INSERT INTO support_attachments
                            (id, conversation_id, file_name, content_type, size_bytes, content, created_at)
                        VALUES (:id, :conversationId, :fileName, :contentType, :size, :content, :at)""")
                .param("id", attachment.id())
                .param("conversationId", attachment.conversationId())
                .param("fileName", attachment.fileName())
                .param("contentType", attachment.contentType())
                .param("size", attachment.sizeBytes())
                .param("content", content)
                .param("at", at.atOffset(ZoneOffset.UTC))
                .update();
    }

    @Override
    public Optional<SupportAttachment> findAttachment(UUID attachmentId) {
        return jdbcClient.sql("""
                        SELECT id, conversation_id, file_name, content_type, size_bytes
                        FROM support_attachments WHERE id = :id""")
                .param("id", attachmentId)
                .query((rs, rowNum) -> new SupportAttachment(rs.getObject("id", UUID.class),
                        rs.getObject("conversation_id", UUID.class), rs.getString("file_name"),
                        rs.getString("content_type"), rs.getLong("size_bytes")))
                .optional();
    }

    @Override
    public Optional<byte[]> findAttachmentContent(UUID attachmentId) {
        return jdbcClient.sql("SELECT content FROM support_attachments WHERE id = :id")
                .param("id", attachmentId)
                .query((rs, rowNum) -> rs.getBytes("content"))
                .optional();
    }

    private static SupportMessage message(ResultSet rs, int rowNum) throws SQLException {
        UUID conversationId = rs.getObject("conversation_id", UUID.class);
        UUID attachmentId = rs.getObject("attachment_id", UUID.class);
        String event = rs.getString("event");
        String suggestions = rs.getString("suggestions");
        return new SupportMessage(rs.getObject("id", UUID.class), conversationId,
                MessageSender.valueOf(rs.getString("sender")), rs.getObject("sender_id", UUID.class), rs.getString("text"),
                event == null ? null : ConversationEvent.valueOf(event), rs.getBoolean("asks_feedback"),
                attachmentId == null ? null : new SupportAttachment(attachmentId, conversationId, rs.getString("file_name"),
                        rs.getString("content_type"), rs.getLong("size_bytes")),
                suggestions == null ? List.of() : List.of(suggestions.split(SUGGESTION_SEPARATOR)),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }
}
