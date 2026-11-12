package com.irvinsegura.wallet.support.application;

import com.irvinsegura.wallet.identity.UserDirectory;
import com.irvinsegura.wallet.support.SupportCaseAssigned;
import com.irvinsegura.wallet.support.domain.model.ConversationEvent;
import com.irvinsegura.wallet.support.domain.model.SupportConversation;
import com.irvinsegura.wallet.support.domain.model.SupportMessage;
import com.irvinsegura.wallet.support.domain.port.SupportAgentAvailabilityRepository;
import com.irvinsegura.wallet.support.domain.port.SupportConversationRepository;
import com.irvinsegura.wallet.support.domain.port.SupportMessageRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Matches the queue with the agents who aren't attending a case and haven't paused: the most urgent conversation, and
 * then the oldest, goes to whoever has waited longest for one. It runs whenever either side changes.
 *
 * <p>Whoever may end up here takes {@link SupportConversationRepository#lockAssignments()} before locking any
 * conversation that is with people. In the other order, this could wait on that conversation while its owner waits
 * for the lock.
 */
@Component
class ConversationAssignments {

    // Each agent could be the owner of a conversation in the queue, which they can't attend themselves
    private static final int CANDIDATES_PER_AGENT = 2;

    private final SupportConversationRepository conversations;
    private final SupportMessageRepository messages;
    private final SupportAgentAvailabilityRepository availability;
    private final UserDirectory users;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    ConversationAssignments(SupportConversationRepository conversations, SupportMessageRepository messages,
            SupportAgentAvailabilityRepository availability, UserDirectory users, ApplicationEventPublisher events,
            Clock clock) {
        this.conversations = conversations;
        this.messages = messages;
        this.availability = availability;
        this.users = users;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    void assignWaiting() {
        assignWaiting(null, null);
    }

    /**
     * @param skippedCase  a case that should not go back to {@code skippedAgent} in this round, because they just
     *                     gave it up; {@code null} for none
     * @param skippedAgent who gave it up
     */
    @Transactional
    void assignWaiting(UUID skippedCase, UUID skippedAgent) {
        conversations.lockAssignments();
        List<UUID> free = new ArrayList<>(users.activeSupportAgents());
        free.removeAll(conversations.findBusy(free));
        free.removeAll(availability.findPaused(free));
        if (free.isEmpty()) {
            return;
        }
        Map<UUID, Instant> lastCase = conversations.findLastAssignments(free);
        free.sort(Comparator.comparing((UUID agent) -> lastCase.getOrDefault(agent, Instant.EPOCH))
                .thenComparing(Comparator.naturalOrder()));
        Instant now = clock.instant();
        for (SupportConversation conversation : conversations.findQueuedForUpdate(free.size() * CANDIDATES_PER_AGENT)) {
            Optional<UUID> agent = free.stream()
                    .filter(candidate -> !candidate.equals(conversation.userId()))
                    .filter(candidate -> !(conversation.id().equals(skippedCase) && candidate.equals(skippedAgent)))
                    .findFirst();
            if (agent.isPresent()) {
                free.remove(agent.get());
                conversation.assignTo(agent.get(), now);
                conversations.save(conversation);
                messages.add(SupportMessage.event(conversation.id(), ConversationEvent.ASSIGNED, now));
                events.publishEvent(new SupportCaseAssigned(conversation.id(), conversation.userId(), agent.get(), now));
            }
        }
    }

    /**
     * Back to the queue goes the case of someone who can no longer attend it, and to someone else if there is anyone.
     */
    @Transactional
    void release(UUID agentId) {
        conversations.lockAssignments();
        conversations.findAssignedToForUpdate(agentId).ifPresent(conversation -> {
            conversation.release();
            conversations.save(conversation);
            messages.add(SupportMessage.event(conversation.id(), ConversationEvent.RELEASED, clock.instant()));
        });
        assignWaiting();
    }
}
