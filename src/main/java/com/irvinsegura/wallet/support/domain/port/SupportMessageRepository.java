package com.irvinsegura.wallet.support.domain.port;

import com.irvinsegura.wallet.support.domain.model.MessageSender;
import com.irvinsegura.wallet.support.domain.model.SupportAttachment;
import com.irvinsegura.wallet.support.domain.model.SupportMessage;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupportMessageRepository {

    /**
     * Stores the message and moves its conversation up in the lists.
     */
    void add(SupportMessage message);

    /**
     * Every message of the conversation in the order they were written, with what is attached but not its bytes.
     */
    List<SupportMessage> findByConversation(UUID conversationId);

    /**
     * When the sender last wrote in the conversation; empty if they never did.
     */
    Optional<Instant> findLastAt(UUID conversationId, MessageSender sender);

    void addAttachment(SupportAttachment attachment, byte[] content, Instant at);

    Optional<SupportAttachment> findAttachment(UUID attachmentId);

    Optional<byte[]> findAttachmentContent(UUID attachmentId);
}
