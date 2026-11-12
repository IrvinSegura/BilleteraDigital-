package com.irvinsegura.wallet.support.domain.port;

import com.irvinsegura.wallet.support.domain.model.SupportTicket;
import com.irvinsegura.wallet.support.domain.model.TicketStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupportTicketRepository {

    /**
     * @return false when the operation already has an open ticket, and nothing was saved
     */
    boolean insert(SupportTicket ticket);

    void update(SupportTicket ticket);

    Optional<SupportTicket> findById(UUID id);

    Optional<SupportTicket> findByIdForUpdate(UUID id);

    List<SupportTicket> findByConversation(UUID conversationId);

    /**
     * Newest first, or the oldest first for open ones: those waiting longest go at the top.
     *
     * @param status null for any
     */
    TicketSlice page(TicketStatus status, int page, int size);

    long countOpen();

    /**
     * The tickets one agent opened, in the order of {@link #page}.
     *
     * @param status null for any
     */
    TicketSlice pageOpenedBy(UUID agentId, TicketStatus status, int page, int size);

    long countOpenOpenedBy(UUID agentId);

    record TicketSlice(List<SupportTicket> items, boolean hasNext) {
    }
}
