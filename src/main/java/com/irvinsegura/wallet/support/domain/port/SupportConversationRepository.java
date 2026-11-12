package com.irvinsegura.wallet.support.domain.port;

import com.irvinsegura.wallet.support.domain.model.CaseResolution;
import com.irvinsegura.wallet.support.domain.model.ConversationStatus;
import com.irvinsegura.wallet.support.domain.model.SupportConversation;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface SupportConversationRepository {

    /**
     * The user's open conversation, locked until the transaction ends, starting one if there is none. Two messages at
     * once find the same one.
     */
    SupportConversation openForUpdate(UUID userId, Instant at);

    /**
     * The user's open conversation, locked until the transaction ends.
     */
    Optional<SupportConversation> findOpenForUpdate(UUID userId);

    Optional<SupportConversation> findOpen(UUID userId);

    /**
     * The newest of the user's closed conversations, if an agent attended it and the user hasn't rated it.
     */
    Optional<SupportConversation> findAwaitingSurvey(UUID userId);

    Optional<SupportConversation> findAwaitingSurveyForUpdate(UUID userId);

    Optional<SupportConversation> findById(UUID id);

    Optional<SupportConversation> findByIdForUpdate(UUID id);

    void save(SupportConversation conversation);

    /**
     * Makes whoever assigns cases wait for their turn until the transaction ends, so no two give away the same agent.
     */
    void lockAssignments();

    /**
     * The conversations waiting for an agent, those with a higher priority first and then the oldest, locked until the
     * transaction ends.
     */
    List<SupportConversation> findQueuedForUpdate(int limit);

    /**
     * Those of the agents who are attending a case.
     */
    Set<UUID> findBusy(Collection<UUID> agents);

    /**
     * When each of the agents last got a case; those who never did are left out.
     */
    Map<UUID, Instant> findLastAssignments(Collection<UUID> agents);

    Optional<SupportConversation> findAssignedToForUpdate(UUID agentId);

    Optional<SupportConversation> findAssignedTo(UUID agentId);

    /**
     * How many conversations are ahead of this one in the queue: those of a higher priority, and those of its own
     * that came before it.
     */
    long countAhead(SupportConversation conversation);

    /**
     * The cases of an agent that match the search, the open one first and then the most recent.
     */
    ConversationSlice findByAgent(UUID agentId, CaseSearch search, int page, int size);

    /**
     * Every conversation that reached the queue and matches the search, the most recent first.
     */
    ConversationSlice findEscalated(CaseSearch search, int page, int size);

    /**
     * The customer's other conversations that reached a person, the most recent first.
     */
    List<ConversationRow> findEscalatedByUser(UUID userId, UUID except, int limit);

    SupportCounts count();

    QueueState queueState();

    /**
     * Cases with an agent whose last word, before {@code before}, was the agent's: the customer hasn't answered.
     */
    List<UUID> findAwaitingCustomer(Instant before);

    /**
     * Cases with an agent whose customer was warned, before {@code before}, that it would close, and said nothing.
     */
    List<UUID> findWarnedBefore(Instant before);

    /**
     * How each of the agents did with the cases they got since {@code since}; those who got none are left out.
     */
    List<AgentStats> agentStats(Collection<UUID> agents, Instant since);

    /**
     * What a list of cases is narrowed to.
     *
     * @param status    {@code null} for any
     * @param byText    whether the list is narrowed to {@code customers} or {@code folio}
     * @param customers the customers whose cases match what was typed
     * @param folio     the beginning of a case's id that was typed; {@code null} if it can't be one
     */
    record CaseSearch(ConversationStatus status, boolean byText, Collection<UUID> customers, String folio) {

        public static CaseSearch only(ConversationStatus status) {
            return new CaseSearch(status, false, List.of(), null);
        }
    }

    /**
     * @param highPriority     how many of those waiting are about money
     * @param oldestEscalatedAt since when the one waiting longest has waited; {@code null} if none does
     */
    record QueueState(long queued, long highPriority, Instant oldestEscalatedAt) {
    }

    /**
     * @param attended             the cases they got
     * @param firstResponseSeconds on average, from getting a case to their first message in it
     * @param handleSeconds        on average, from getting a case to closing it
     * @param averageScore         of the surveys answered about them; {@code null} if none was
     */
    record AgentStats(UUID agentId, long attended, long resolved, Double firstResponseSeconds, Double handleSeconds,
            long surveys, Double averageScore) {
    }

    /**
     * @param lastText the last thing written in it, to tell cases apart in a list
     */
    record ConversationRow(SupportConversation conversation, String lastText, Instant lastMessageAt) {
    }

    record ConversationSlice(List<ConversationRow> items, boolean hasNext) {
    }

    /**
     * @param averageScore {@code null} until someone answers a survey
     */
    record SupportCounts(long withBot, long queued, long assigned, long resolved, long surveys, Double averageScore,
            long queuedHighPriority, Instant oldestQueuedAt, Map<CaseResolution, Long> resolutions) {
    }
}
