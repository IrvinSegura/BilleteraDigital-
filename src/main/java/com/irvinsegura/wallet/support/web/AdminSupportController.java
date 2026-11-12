package com.irvinsegura.wallet.support.web;

import com.irvinsegura.wallet.shared.domain.Language;
import com.irvinsegura.wallet.support.application.SupportAdministrationService;
import com.irvinsegura.wallet.support.application.SupportAdministrationService.AgentReport;
import com.irvinsegura.wallet.support.application.SupportAdministrationService.SuggestionPage;
import com.irvinsegura.wallet.support.application.SupportAdministrationService.SuggestionView;
import com.irvinsegura.wallet.support.domain.model.BotMissKind;
import com.irvinsegura.wallet.support.domain.model.BotSuggestion;
import com.irvinsegura.wallet.support.domain.model.ConversationStatus;
import com.irvinsegura.wallet.support.domain.port.SupportBotQuestionRepository.BotMiss;
import com.irvinsegura.wallet.support.domain.port.SupportBotQuestionRepository.BotMissSlice;
import com.irvinsegura.wallet.support.domain.port.SupportBotQuestionRepository.BotSummary;
import com.irvinsegura.wallet.support.domain.port.SupportConversationRepository.SupportCounts;
import com.irvinsegura.wallet.support.web.ConversationResponses.ConversationPageResponse;
import com.irvinsegura.wallet.support.web.AgentResponses.AgentMetricsResponse;
import com.irvinsegura.wallet.support.web.ConversationResponses.ConversationResponse;
import com.irvinsegura.wallet.support.web.ConversationResponses.NoteResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * How support is doing. Read-only but for reviewing what agents suggest the bot learn: the ADMIN role is enforced for
 * all of {@code /api/v1/admin/**}.
 */
@RestController
@RequestMapping("/api/v1/admin/support")
@SecurityRequirement(name = "bearerAuth")
class AdminSupportController {

    private final SupportAdministrationService administration;

    AdminSupportController(SupportAdministrationService administration) {
        this.administration = administration;
    }

    /**
     * How many conversations are in each state, and the average of their surveys.
     */
    @GetMapping("/summary")
    SupportCounts summary() {
        return administration.summary();
    }

    /**
     * Every conversation that reached a person or the queue, the most recent first; {@code status} narrows it down,
     * and {@code q} to a customer's name or email, or a case's folio.
     */
    @GetMapping("/conversations")
    ConversationPageResponse list(@RequestParam(required = false) ConversationStatus status,
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ConversationPageResponse.from(administration.list(status, q, page, size));
    }

    @GetMapping("/conversations/{conversationId}")
    AdminConversationResponse get(@PathVariable UUID conversationId) {
        var view = administration.get(conversationId);
        return new AdminConversationResponse(ConversationResponse.from(view), view.conversation().surveyScore(),
                view.conversation().surveyComment(), NoteResponse.from(administration.notes(conversationId)));
    }

    /**
     * Every active agent and how they did with the cases they got in the last {@code days}.
     */
    @GetMapping("/agents")
    List<AgentReportResponse> agents(@RequestParam(defaultValue = "30") @Min(1) @Max(365) int days) {
        return administration.agents(days).stream().map(report -> AgentReportResponse.from(days, report)).toList();
    }

    /**
     * What agents think the bot should answer by itself: open ones the oldest first.
     *
     * @param status {@code OPEN}, {@code ADDED} or {@code DISMISSED}; any if left out
     */
    @GetMapping("/bot/suggestions")
    BotSuggestionPageResponse suggestions(@RequestParam(required = false) BotSuggestion.Status status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        SuggestionPage found = administration.suggestions(status, page, size);
        return new BotSuggestionPageResponse(found.items().stream().map(AdminBotSuggestionResponse::from).toList(),
                found.page(), found.size(), found.hasNext(), found.open());
    }

    /**
     * Marks a suggestion added to the bot's knowledge files, or dismissed.
     */
    @PostMapping("/bot/suggestions/{suggestionId}/review")
    AdminBotSuggestionResponse review(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID suggestionId,
            @Valid @RequestBody ReviewSuggestionRequest request) {
        return AdminBotSuggestionResponse.from(administration.review(suggestionId, request.status(),
                UUID.fromString(jwt.getSubject())));
    }

    /**
     * What the bot did with the questions of the last {@code days}.
     */
    @GetMapping("/bot/summary")
    BotSummary botSummary(@RequestParam(defaultValue = "30") @Min(1) @Max(365) int days) {
        return administration.botSummary(days);
    }

    /**
     * The questions where the bot failed, the same one written the same way counted once and the most repeated first:
     * what to add to its knowledge files. Without {@code kind}, every kind but {@code ASKED_AGAIN}, which is mostly
     * noise.
     *
     * @param language {@code es} or {@code en}; both if left out
     */
    @GetMapping("/bot/misses")
    BotMissPageResponse botMisses(@RequestParam(required = false) BotMissKind kind,
            @RequestParam(required = false) @Pattern(regexp = "es|en") String language,
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int days,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        BotMissSlice slice = administration.botMisses(kind, language == null ? null : Language.ofTag(language).orElseThrow(),
                days, page, size);
        return new BotMissPageResponse(slice.items().stream().map(BotMissResponse::from).toList(), page, size,
                slice.hasNext());
    }

    /**
     * @param surveyScore   from 1 to 5; {@code null} if not rated
     * @param surveyComment what the user added to it, if anything
     * @param notes         what the team wrote on it for each other
     */
    record AdminConversationResponse(ConversationResponse conversation, Integer surveyScore, String surveyComment,
            List<NoteResponse> notes) {
    }

    /**
     * @param busy      attending a case now
     * @param available taking new cases, or paused
     */
    record AgentReportResponse(UUID id, String name, boolean busy, boolean available, AgentMetricsResponse metrics) {

        static AgentReportResponse from(int days, AgentReport report) {
            return new AgentReportResponse(report.id(), report.name(), report.busy(), report.available(),
                    AgentMetricsResponse.from(days, report.stats()));
        }
    }

    /**
     * @param language     {@code es} or {@code en}: the knowledge files it belongs in
     * @param status       {@code OPEN}, {@code ADDED} or {@code DISMISSED}
     * @param reviewedByName who added or dismissed it; {@code null} while open
     */
    record AdminBotSuggestionResponse(UUID id, UUID conversationId, String agentName, String language,
            String question, String answer, String status, Instant createdAt, String reviewedByName,
            Instant reviewedAt) {

        static AdminBotSuggestionResponse from(SuggestionView view) {
            BotSuggestion suggestion = view.suggestion();
            return new AdminBotSuggestionResponse(suggestion.id(), suggestion.conversationId(), view.agentName(),
                    suggestion.language().tag(), suggestion.question(), suggestion.answer(),
                    suggestion.status().name(), suggestion.createdAt(), view.reviewedByName(),
                    suggestion.reviewedAt());
        }
    }

    /**
     * @param open how many wait for a review, whatever the page shows
     */
    record BotSuggestionPageResponse(List<AdminBotSuggestionResponse> items, int page, int size, boolean hasNext,
            long open) {
    }

    record ReviewSuggestionRequest(@NotNull BotSuggestion.Status status) {

        @AssertTrue(message = "status must be ADDED or DISMISSED")
        boolean isReviewed() {
            return status != BotSuggestion.Status.OPEN;
        }
    }

    /**
     * @param question       how it was written the last time
     * @param language       {@code es} or {@code en}: the knowledge files it was answered from
     * @param topics         the answer that didn't help, or the ones offered; empty otherwise
     * @param times          how many times it was asked this way
     * @param conversationId where it was asked the last time
     */
    record BotMissResponse(BotMissKind kind, String language, String question, List<String> topics, long times,
            Instant lastAskedAt, UUID conversationId) {

        static BotMissResponse from(BotMiss miss) {
            return new BotMissResponse(miss.kind(), miss.language().tag(), miss.question(),
                    miss.topic() == null ? List.of() : miss.topic().lines().toList(), miss.times(), miss.lastAskedAt(),
                    miss.conversationId());
        }
    }

    record BotMissPageResponse(List<BotMissResponse> items, int page, int size, boolean hasNext) {
    }
}
