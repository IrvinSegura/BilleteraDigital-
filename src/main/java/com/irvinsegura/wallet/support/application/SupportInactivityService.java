package com.irvinsegura.wallet.support.application;

import com.irvinsegura.wallet.support.SupportCaseResolved;
import com.irvinsegura.wallet.support.domain.model.CaseResolution;
import com.irvinsegura.wallet.support.domain.model.ConversationEvent;
import com.irvinsegura.wallet.support.domain.model.ConversationStatus;
import com.irvinsegura.wallet.support.domain.model.MessageSender;
import com.irvinsegura.wallet.support.domain.model.SupportConversation;
import com.irvinsegura.wallet.support.domain.model.SupportMessage;
import com.irvinsegura.wallet.support.domain.model.TicketStatus;
import com.irvinsegura.wallet.support.domain.port.SupportConversationRepository;
import com.irvinsegura.wallet.support.domain.port.SupportMessageRepository;
import com.irvinsegura.wallet.support.domain.port.SupportTicketRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Frees agents from customers who stopped answering: after {@code warn-after} without a reply to the agent, the
 * customer is told the case will close; after {@code close-after} more, it closes as {@link CaseResolution#NO_REPLY}
 * and the agent gets the next one in the queue. Writing anything in between keeps it open. A case with a ticket open
 * waits on the administrators, not on the customer, so it is left alone.
 */
@Service
public class SupportInactivityService {

    private final SupportConversationRepository conversations;
    private final SupportMessageRepository messages;
    private final SupportTicketRepository tickets;
    private final ConversationAssignments assignments;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final Duration warnAfter;
    private final Duration closeAfter;

    SupportInactivityService(SupportConversationRepository conversations, SupportMessageRepository messages,
            SupportTicketRepository tickets, ConversationAssignments assignments, ApplicationEventPublisher events,
            Clock clock, @Value("${wallet.support.inactivity.warn-after}") Duration warnAfter,
            @Value("${wallet.support.inactivity.close-after}") Duration closeAfter) {
        this.conversations = conversations;
        this.messages = messages;
        this.tickets = tickets;
        this.assignments = assignments;
        this.events = events;
        this.clock = clock;
        this.warnAfter = warnAfter;
        this.closeAfter = closeAfter;
    }

    /**
     * @param warned how many customers were told their case will close
     * @param closed how many cases were closed
     */
    public record Outcome(int warned, int closed) {
    }

    /**
     * Instances running it at once is harmless: each case is checked again under its row lock, which a message from
     * the customer takes too, so a case they just answered is neither warned nor closed.
     */
    @Transactional
    public Outcome handleInactiveCases() {
        conversations.lockAssignments();
        Instant now = clock.instant();
        Instant closeBefore = now.minus(closeAfter);
        int closed = 0;
        for (UUID id : conversations.findWarnedBefore(closeBefore)) {
            Optional<SupportConversation> found = lockIfStill(id, closeBefore,
                    last -> last.event() == ConversationEvent.INACTIVITY_WARNING);
            if (found.isPresent()) {
                SupportConversation conversation = found.get();
                conversation.resolve(now, CaseResolution.NO_REPLY);
                conversations.save(conversation);
                messages.add(SupportMessage.event(id, ConversationEvent.INACTIVITY_CLOSED, now));
                events.publishEvent(new SupportCaseResolved(id, conversation.userId(), conversation.agentId(), now));
                closed++;
            }
        }
        Instant warnBefore = now.minus(warnAfter);
        int warned = 0;
        for (UUID id : conversations.findAwaitingCustomer(warnBefore)) {
            if (lockIfStill(id, warnBefore, last -> last.sender() == MessageSender.AGENT).isPresent()) {
                messages.add(SupportMessage.event(id, ConversationEvent.INACTIVITY_WARNING, now));
                warned++;
            }
        }
        if (closed > 0) {
            assignments.assignWaiting();
        }
        return new Outcome(warned, closed);
    }

    // The case, locked, if it still waits on the customer since before the time, with no ticket open
    private Optional<SupportConversation> lockIfStill(UUID id, Instant before, Predicate<SupportMessage> lastWordIs) {
        return conversations.findByIdForUpdate(id)
                .filter(conversation -> conversation.status() == ConversationStatus.ASSIGNED)
                .filter(conversation -> lastWord(conversation)
                        .filter(last -> last.createdAt().isBefore(before) && lastWordIs.test(last))
                        .isPresent())
                .filter(conversation -> tickets.findByConversation(id).stream()
                        .noneMatch(ticket -> ticket.status() == TicketStatus.OPEN));
    }

    // What people wrote since the agent got it, and the warning; see SupportConversationRepository#findAwaitingCustomer
    private Optional<SupportMessage> lastWord(SupportConversation conversation) {
        List<SupportMessage> written = messages.findByConversation(conversation.id());
        return written.reversed().stream()
                .filter(message -> message.sender() != MessageSender.SYSTEM
                        || message.event() == ConversationEvent.INACTIVITY_WARNING)
                .findFirst()
                .filter(message -> !message.createdAt().isBefore(conversation.assignedAt()));
    }
}
