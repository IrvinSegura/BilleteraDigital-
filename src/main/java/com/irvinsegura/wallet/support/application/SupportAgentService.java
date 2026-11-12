package com.irvinsegura.wallet.support.application;

import com.irvinsegura.wallet.identity.UserContact;
import com.irvinsegura.wallet.identity.UserDirectory;
import com.irvinsegura.wallet.shared.domain.Language;
import com.irvinsegura.wallet.support.SupportAgentReplied;
import com.irvinsegura.wallet.support.SupportCaseAssigned;
import com.irvinsegura.wallet.support.SupportCaseResolved;
import com.irvinsegura.wallet.support.domain.model.AgentNotAvailableException;
import com.irvinsegura.wallet.support.domain.model.AttachmentRules;
import com.irvinsegura.wallet.support.domain.model.BotSuggestion;
import com.irvinsegura.wallet.support.domain.model.CaseNote;
import com.irvinsegura.wallet.support.domain.model.CaseResolution;
import com.irvinsegura.wallet.support.domain.model.ConversationEvent;
import com.irvinsegura.wallet.support.domain.model.ConversationNotFoundException;
import com.irvinsegura.wallet.support.domain.model.ConversationStatus;
import com.irvinsegura.wallet.support.domain.model.MessageSender;
import com.irvinsegura.wallet.support.domain.model.SupportAttachment;
import com.irvinsegura.wallet.support.domain.model.SupportConversation;
import com.irvinsegura.wallet.support.domain.model.SupportMessage;
import com.irvinsegura.wallet.support.domain.port.SupportAgentAvailabilityRepository;
import com.irvinsegura.wallet.support.domain.port.SupportBotSuggestionRepository;
import com.irvinsegura.wallet.support.domain.port.SupportCaseNoteRepository;
import com.irvinsegura.wallet.support.domain.port.SupportConversationRepository;
import com.irvinsegura.wallet.support.domain.port.SupportConversationRepository.AgentStats;
import com.irvinsegura.wallet.support.domain.port.SupportConversationRepository.QueueState;
import com.irvinsegura.wallet.support.domain.port.SupportMessageRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The chat as an agent lives it. An agent only reaches the cases given to them, and sees of the user what the case
 * needs: their name, the conversation and their earlier cases, never their email or balances. An agent may pause to
 * stop getting new cases, give a case back to the queue or pass it to a free colleague, and leave notes on it that
 * only the team reads.
 */
@Service
public class SupportAgentService {

    // Enough to see whether the customer keeps coming back with the same problem
    private static final int HISTORY_SIZE = 20;

    private final SupportConversationRepository conversations;
    private final SupportMessageRepository messages;
    private final SupportAgentAvailabilityRepository availability;
    private final SupportCaseNoteRepository notes;
    private final SupportBotSuggestionRepository suggestions;
    private final ConversationAssignments assignments;
    private final ConversationViews views;
    private final UserDirectory users;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    SupportAgentService(SupportConversationRepository conversations, SupportMessageRepository messages,
            SupportAgentAvailabilityRepository availability, SupportCaseNoteRepository notes,
            SupportBotSuggestionRepository suggestions, ConversationAssignments assignments, ConversationViews views,
            UserDirectory users, ApplicationEventPublisher events, Clock clock) {
        this.conversations = conversations;
        this.messages = messages;
        this.availability = availability;
        this.notes = notes;
        this.suggestions = suggestions;
        this.assignments = assignments;
        this.views = views;
        this.users = users;
        this.events = events;
        this.clock = clock;
    }

    /**
     * @param activeConversationId  the case the agent is attending; {@code null} if none
     * @param queued                how many conversations wait for an agent
     * @param available             whether new cases come to them; a paused agent keeps the one they have
     * @param queuedHighPriority    how many of those waiting are about money
     * @param oldestQueuedAt        since when the one waiting longest has waited; {@code null} if none does
     * @param lastCustomerMessageAt when the customer of the active case last wrote, to tell the agent; {@code null}
     *                              if they haven't or there is no active case
     */
    public record AgentSummary(UUID activeConversationId, long queued, boolean available, long queuedHighPriority,
            Instant oldestQueuedAt, Instant lastCustomerMessageAt) {
    }

    @Transactional(readOnly = true)
    public AgentSummary summary(UUID agentId) {
        Optional<SupportConversation> active = conversations.findAssignedTo(agentId);
        QueueState queue = conversations.queueState();
        return new AgentSummary(active.map(SupportConversation::id).orElse(null), queue.queued(),
                availability.findPaused(List.of(agentId)).isEmpty(), queue.highPriority(), queue.oldestEscalatedAt(),
                active.flatMap(found -> messages.findLastAt(found.id(), MessageSender.USER)).orElse(null));
    }

    /**
     * Pausing stops new cases, without taking away the one being attended; coming back may bring the next one in the
     * queue at once.
     */
    @Transactional
    public AgentSummary setAvailability(UUID agentId, boolean available) {
        conversations.lockAssignments();
        availability.save(agentId, available, clock.instant());
        if (available) {
            assignments.assignWaiting();
        }
        return summary(agentId);
    }

    /**
     * @param status {@code null} for any
     * @param text   the customer's name or the case's folio; {@code null} or blank for every case
     */
    @Transactional(readOnly = true)
    public ConversationPage list(UUID agentId, ConversationStatus status, String text, int page, int size) {
        return views.page(conversations.findByAgent(agentId, CaseSearches.of(status, text, false, users), page, size),
                page, size);
    }

    /**
     * @throws ConversationNotFoundException if it isn't a case of this agent
     */
    @Transactional(readOnly = true)
    public ConversationView get(UUID agentId, UUID conversationId) {
        findCase(agentId, conversationId);
        return views.forStaff(conversationId);
    }

    /**
     * The customer's earlier cases with people, the most recent first, to see whether they come back with the same
     * problem.
     *
     * @throws ConversationNotFoundException if it isn't a case of this agent
     */
    @Transactional(readOnly = true)
    public List<ConversationSummary> history(UUID agentId, UUID conversationId) {
        SupportConversation conversation = findCase(agentId, conversationId);
        return views.summaries(conversations.findEscalatedByUser(conversation.userId(), conversationId, HISTORY_SIZE));
    }

    /**
     * A note and who wrote it.
     */
    public record NoteView(CaseNote note, String authorName) {
    }

    /**
     * @throws ConversationNotFoundException if it isn't a case of this agent
     */
    @Transactional(readOnly = true)
    public List<NoteView> notes(UUID agentId, UUID conversationId) {
        findCase(agentId, conversationId);
        return notesOf(conversationId);
    }

    /**
     * Adds a note only the team reads, also once the case is closed: what was found out may matter next time.
     *
     * @throws ConversationNotFoundException if it isn't a case of this agent
     */
    @Transactional
    public List<NoteView> addNote(UUID agentId, UUID conversationId, String text) {
        findCase(agentId, conversationId);
        notes.add(CaseNote.write(conversationId, agentId, text, clock.instant()));
        return notesOf(conversationId);
    }

    List<NoteView> notesOf(UUID conversationId) {
        List<CaseNote> written = notes.findByConversation(conversationId);
        Map<UUID, String> names = users.findNames(written.stream().map(CaseNote::authorId).distinct().toList());
        return written.stream().map(note -> new NoteView(note, names.getOrDefault(note.authorId(), ""))).toList();
    }

    @Transactional
    public ConversationView reply(UUID agentId, UUID conversationId, String text) {
        SupportConversation conversation = lockCase(agentId, conversationId);
        conversation.requireOpen();
        Instant now = clock.instant();
        messages.add(SupportMessage.fromPerson(conversationId, MessageSender.AGENT, agentId, text, null, now));
        events.publishEvent(new SupportAgentReplied(conversationId, conversation.userId(), now));
        return views.forStaff(conversationId);
    }

    /**
     * @throws com.irvinsegura.wallet.support.domain.model.AttachmentNotAllowedException if it can't be sent
     */
    @Transactional
    public ConversationView attach(UUID agentId, UUID conversationId, String fileName, byte[] content, String text) {
        SupportConversation conversation = lockCase(agentId, conversationId);
        conversation.requireOpen();
        Instant now = clock.instant();
        SupportAttachment attachment = AttachmentRules.describe(conversationId, fileName, content);
        messages.addAttachment(attachment, content, now);
        messages.add(SupportMessage.fromPerson(conversationId, MessageSender.AGENT, agentId, text, attachment, now));
        events.publishEvent(new SupportAgentReplied(conversationId, conversation.userId(), now));
        return views.forStaff(conversationId);
    }

    /**
     * Closes the case with how it ended, which asks the user how it went and leaves the agent free for the next one
     * in the queue.
     *
     * @param resolution one of {@link CaseResolution#BY_AGENT}
     */
    @Transactional
    public ConversationView resolve(UUID agentId, UUID conversationId, CaseResolution resolution) {
        if (!CaseResolution.BY_AGENT.contains(resolution)) {
            throw new IllegalArgumentException("An agent can't close a case as " + resolution);
        }
        conversations.lockAssignments();
        SupportConversation conversation = lockCase(agentId, conversationId);
        Instant now = clock.instant();
        conversation.resolve(now, resolution);
        conversations.save(conversation);
        messages.add(SupportMessage.event(conversationId, ConversationEvent.RESOLVED, now));
        events.publishEvent(new SupportCaseResolved(conversationId, conversation.userId(), agentId, now));
        assignments.assignWaiting();
        return views.forStaff(conversationId);
    }

    /**
     * A colleague, whether they are attending a case now and whether they take new ones.
     */
    public record Colleague(UUID id, String name, boolean busy, boolean available) {
    }

    /**
     * The other active agents, by name, for passing a case on.
     */
    @Transactional(readOnly = true)
    public List<Colleague> colleagues(UUID agentId) {
        List<UUID> team = users.activeSupportAgents().stream().filter(other -> !other.equals(agentId)).toList();
        Set<UUID> busy = conversations.findBusy(team);
        Set<UUID> paused = availability.findPaused(team);
        Map<UUID, String> names = users.findNames(team);
        return team.stream()
                .map(other -> new Colleague(other, names.getOrDefault(other, ""), busy.contains(other),
                        !paused.contains(other)))
                .sorted(Comparator.comparing(Colleague::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /**
     * Gives the case back to the queue, for the next free colleague; it skips this agent in this round, who may get
     * another case instead. If no one else is free, it waits for whoever frees up first.
     */
    @Transactional
    public void release(UUID agentId, UUID conversationId) {
        conversations.lockAssignments();
        SupportConversation conversation = lockCase(agentId, conversationId);
        conversation.requireOpen();
        conversation.release();
        conversations.save(conversation);
        messages.add(SupportMessage.event(conversationId, ConversationEvent.RELEASED, clock.instant()));
        assignments.assignWaiting(conversationId, agentId);
    }

    /**
     * Passes the case to a colleague who is free and hasn't paused, who is told as with any case given to them; this
     * agent is then free for the next one in the queue.
     *
     * @throws AgentNotAvailableException if the colleague isn't an active agent free to take it
     */
    @Transactional
    public void transfer(UUID agentId, UUID conversationId, UUID colleagueId) {
        conversations.lockAssignments();
        SupportConversation conversation = lockCase(agentId, conversationId);
        conversation.requireOpen();
        List<UUID> colleague = List.of(colleagueId);
        if (colleagueId.equals(agentId) || colleagueId.equals(conversation.userId())
                || !users.activeSupportAgents().contains(colleagueId)
                || !conversations.findBusy(colleague).isEmpty() || !availability.findPaused(colleague).isEmpty()) {
            throw new AgentNotAvailableException();
        }
        Instant now = clock.instant();
        conversation.transferTo(colleagueId, now);
        conversations.save(conversation);
        messages.add(SupportMessage.event(conversationId, ConversationEvent.TRANSFERRED, now));
        events.publishEvent(new SupportCaseAssigned(conversationId, conversation.userId(), colleagueId, now));
        assignments.assignWaiting();
    }

    /**
     * How the agent did with the cases they got in the last {@code days}.
     */
    @Transactional(readOnly = true)
    public AgentStats metrics(UUID agentId, int days) {
        return conversations.agentStats(List.of(agentId), clock.instant().minus(Duration.ofDays(days))).stream()
                .findFirst()
                .orElse(new AgentStats(agentId, 0, 0, null, null, 0, null));
    }

    /**
     * Proposes that the bot answer a question by itself, as the agent did in this case, in the customer's language.
     *
     * @throws ConversationNotFoundException if it isn't a case of this agent
     */
    @Transactional
    public BotSuggestion suggestToBot(UUID agentId, UUID conversationId, String question, String answer) {
        SupportConversation conversation = findCase(agentId, conversationId);
        Language language = users.findContact(conversation.userId()).map(UserContact::language)
                .orElse(Language.DEFAULT);
        BotSuggestion suggestion = BotSuggestion.propose(conversationId, agentId, language, question, answer,
                clock.instant());
        suggestions.insert(suggestion);
        return suggestion;
    }

    private SupportConversation findCase(UUID agentId, UUID conversationId) {
        return conversations.findById(conversationId).filter(found -> agentId.equals(found.agentId()))
                .orElseThrow(ConversationNotFoundException::new);
    }

    private SupportConversation lockCase(UUID agentId, UUID conversationId) {
        return conversations.findByIdForUpdate(conversationId).filter(found -> agentId.equals(found.agentId()))
                .orElseThrow(ConversationNotFoundException::new);
    }
}
