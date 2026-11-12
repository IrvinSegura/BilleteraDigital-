package com.irvinsegura.wallet.support.application;

import com.irvinsegura.wallet.identity.UserDirectory;
import com.irvinsegura.wallet.shared.domain.Language;
import com.irvinsegura.wallet.support.application.SupportAgentService.NoteView;
import com.irvinsegura.wallet.support.domain.model.BotMissKind;
import com.irvinsegura.wallet.support.domain.model.BotSuggestion;
import com.irvinsegura.wallet.support.domain.model.BotSuggestionNotFoundException;
import com.irvinsegura.wallet.support.domain.model.ConversationStatus;
import com.irvinsegura.wallet.support.domain.port.SupportAgentAvailabilityRepository;
import com.irvinsegura.wallet.support.domain.port.SupportBotQuestionRepository;
import com.irvinsegura.wallet.support.domain.port.SupportBotSuggestionRepository;
import com.irvinsegura.wallet.support.domain.port.SupportBotSuggestionRepository.SuggestionSlice;
import com.irvinsegura.wallet.support.domain.port.SupportBotQuestionRepository.BotMissSlice;
import com.irvinsegura.wallet.support.domain.port.SupportBotQuestionRepository.BotSummary;
import com.irvinsegura.wallet.support.domain.port.SupportConversationRepository;
import com.irvinsegura.wallet.support.domain.port.SupportConversationRepository.AgentStats;
import com.irvinsegura.wallet.support.domain.port.SupportConversationRepository.SupportCounts;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * How support is doing, for administrators, who read every case, its survey and the team's notes but don't take part
 * in them; how each agent is doing; and where the bot fails or what agents think it should learn, to know what to
 * add to its knowledge files.
 */
@Service
public class SupportAdministrationService {

    private final SupportConversationRepository conversations;
    private final SupportBotQuestionRepository questions;
    private final SupportBotSuggestionRepository suggestions;
    private final SupportAgentAvailabilityRepository availability;
    private final SupportAgentService agents;
    private final ConversationViews views;
    private final UserDirectory users;
    private final Clock clock;

    SupportAdministrationService(SupportConversationRepository conversations, SupportBotQuestionRepository questions,
            SupportBotSuggestionRepository suggestions, SupportAgentAvailabilityRepository availability,
            SupportAgentService agents, ConversationViews views, UserDirectory users, Clock clock) {
        this.conversations = conversations;
        this.questions = questions;
        this.suggestions = suggestions;
        this.availability = availability;
        this.agents = agents;
        this.views = views;
        this.users = users;
        this.clock = clock;
    }

    /**
     * What the bot did with the questions of the last {@code days}.
     */
    @Transactional(readOnly = true)
    public BotSummary botSummary(int days) {
        return questions.summarize(since(days));
    }

    /**
     * The questions of the last {@code days} where the bot failed, the most repeated first.
     *
     * @param kind     {@code null} for the ones worth reading, see {@link BotMissKind#USEFUL}
     * @param language {@code null} for both
     */
    @Transactional(readOnly = true)
    public BotMissSlice botMisses(BotMissKind kind, Language language, int days, int page, int size) {
        return questions.findMisses(kind == null ? BotMissKind.USEFUL : List.of(kind), language, since(days), page, size);
    }

    private Instant since(int days) {
        return clock.instant().minus(Duration.ofDays(days));
    }

    @Transactional(readOnly = true)
    public SupportCounts summary() {
        return conversations.count();
    }

    /**
     * @param status {@code null} for any
     * @param text   the customer's name or email, or the case's folio; {@code null} or blank for every case
     */
    @Transactional(readOnly = true)
    public ConversationPage list(ConversationStatus status, String text, int page, int size) {
        return views.page(conversations.findEscalated(CaseSearches.of(status, text, true, users), page, size), page,
                size);
    }

    @Transactional(readOnly = true)
    public ConversationView get(UUID conversationId) {
        return views.forStaff(conversationId);
    }

    @Transactional(readOnly = true)
    public List<NoteView> notes(UUID conversationId) {
        return agents.notesOf(conversationId);
    }

    /**
     * An agent, whether they are attending a case or paused now, and how they did.
     */
    public record AgentReport(UUID id, String name, boolean busy, boolean available, AgentStats stats) {
    }

    /**
     * Every active agent and how they did with the cases they got in the last {@code days}, by name.
     */
    @Transactional(readOnly = true)
    public List<AgentReport> agents(int days) {
        List<UUID> team = users.activeSupportAgents();
        Map<UUID, String> names = users.findNames(team);
        Set<UUID> busy = conversations.findBusy(team);
        Set<UUID> paused = availability.findPaused(team);
        Map<UUID, AgentStats> stats = conversations.agentStats(team, since(days)).stream()
                .collect(Collectors.toMap(AgentStats::agentId, Function.identity()));
        return team.stream()
                .map(agent -> new AgentReport(agent, names.getOrDefault(agent, ""), busy.contains(agent),
                        !paused.contains(agent),
                        stats.getOrDefault(agent, new AgentStats(agent, 0, 0, null, null, 0, null))))
                .sorted(Comparator.comparing(AgentReport::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /**
     * A suggestion with the names of who made it and who reviewed it.
     */
    public record SuggestionView(BotSuggestion suggestion, String agentName, String reviewedByName) {
    }

    /**
     * @param open how many wait for a review, whatever the page shows
     */
    public record SuggestionPage(List<SuggestionView> items, int page, int size, boolean hasNext, long open) {
    }

    /**
     * @param status {@code null} for any
     */
    @Transactional(readOnly = true)
    public SuggestionPage suggestions(BotSuggestion.Status status, int page, int size) {
        SuggestionSlice slice = suggestions.page(status, page, size);
        Set<UUID> people = new HashSet<>();
        slice.items().forEach(found -> {
            people.add(found.agentId());
            if (found.reviewedBy() != null) {
                people.add(found.reviewedBy());
            }
        });
        Map<UUID, String> names = users.findNames(people);
        return new SuggestionPage(slice.items().stream().map(found -> view(found, names)).toList(), page, size,
                slice.hasNext(), suggestions.countOpen());
    }

    /**
     * Marks it added to the bot's knowledge files, or dismissed.
     *
     * @throws BotSuggestionNotFoundException if there is none with that id
     * @throws com.irvinsegura.wallet.support.domain.model.BotSuggestionReviewedException if it was already reviewed
     */
    @Transactional
    public SuggestionView review(UUID suggestionId, BotSuggestion.Status outcome, UUID administratorId) {
        BotSuggestion suggestion = suggestions.findByIdForUpdate(suggestionId)
                .orElseThrow(BotSuggestionNotFoundException::new);
        suggestion.review(outcome, administratorId, clock.instant());
        suggestions.update(suggestion);
        return view(suggestion, users.findNames(Set.of(suggestion.agentId(), administratorId)));
    }

    private static SuggestionView view(BotSuggestion suggestion, Map<UUID, String> names) {
        return new SuggestionView(suggestion, names.getOrDefault(suggestion.agentId(), ""),
                suggestion.reviewedBy() == null ? null : names.get(suggestion.reviewedBy()));
    }
}
