package com.irvinsegura.wallet.support.web;

import com.irvinsegura.wallet.identity.AccountStanding;
import com.irvinsegura.wallet.support.application.SupportAgentService.Colleague;
import com.irvinsegura.wallet.support.domain.model.BotSuggestion;
import com.irvinsegura.wallet.support.domain.model.CaseNote;
import com.irvinsegura.wallet.support.domain.model.CaseResolution;
import com.irvinsegura.wallet.support.domain.port.SupportConversationRepository.AgentStats;
import com.irvinsegura.wallet.support.application.SupportTicketService.CustomerStanding;
import com.irvinsegura.wallet.support.application.SupportTicketService.WalletState;
import com.irvinsegura.wallet.support.domain.model.SavedReply;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * What the agent reads and sends about the customer's account, the team, their saved replies, notes and how they do.
 */
final class AgentResponses {

    private AgentResponses() {
    }

    /**
     * @param status         {@code ACTIVE}, {@code DISABLED} or {@code CLOSED}
     * @param lockedUntil    until when sign-in is locked; {@code null} if it isn't
     * @param identityStatus {@code NONE}, {@code PENDING_REVIEW}, {@code VERIFIED} or {@code REJECTED}
     */
    record CustomerStandingResponse(String status, boolean emailVerified, Instant lockedUntil, String identityStatus,
            Instant identitySubmittedAt, String identityRejectionReason, boolean identityRequiredForWithdrawals,
            Instant memberSince, List<WalletState> wallets) {

        static CustomerStandingResponse from(CustomerStanding standing) {
            AccountStanding account = standing.account();
            return new CustomerStandingResponse(account.status(), account.emailVerified(), account.lockedUntil(),
                    account.identityStatus(), account.identitySubmittedAt(), account.identityRejectionReason(),
                    account.identityRequiredForWithdrawals(), account.memberSince(), standing.wallets());
        }
    }

    /**
     * @param busy      whether they are attending a case now; only free colleagues can take one
     * @param available whether they take new cases; a paused colleague can't be given one
     */
    record ColleagueResponse(UUID id, String name, boolean busy, boolean available) {

        static List<ColleagueResponse> from(List<Colleague> colleagues) {
            return colleagues.stream()
                    .map(found -> new ColleagueResponse(found.id(), found.name(), found.busy(), found.available()))
                    .toList();
        }
    }

    record AvailabilityRequest(@NotNull Boolean available) {
    }

    /**
     * @param reason how it ended; {@code SOLVED} if left out
     */
    // Named apart from the provider transaction's request, as CaseTransferRequest is
    record CaseResolveRequest(CaseResolution reason) {

        CaseResolution reasonOrDefault() {
            return reason == null ? CaseResolution.SOLVED : reason;
        }

        @AssertTrue(message = "reason must be one an agent can pick: SOLVED, INFORMATION, TICKET, NO_REPLY, DUPLICATE or OTHER")
        boolean isReasonAnAgentsOwn() {
            return CaseResolution.BY_AGENT.contains(reasonOrDefault());
        }
    }

    record NoteRequest(@NotBlank @Size(max = CaseNote.MAX_TEXT)
            @Pattern(regexp = ConversationResponses.MESSAGE_TEXT, message = ConversationResponses.MESSAGE_TEXT_ERROR)
            String text) {
    }

    record BotSuggestionRequest(
            @NotBlank @Size(max = BotSuggestion.MAX_QUESTION)
            @Pattern(regexp = ConversationResponses.MESSAGE_TEXT, message = ConversationResponses.MESSAGE_TEXT_ERROR)
            String question,
            @NotBlank @Size(max = BotSuggestion.MAX_ANSWER)
            @Pattern(regexp = ConversationResponses.MESSAGE_TEXT, message = ConversationResponses.MESSAGE_TEXT_ERROR)
            String answer) {
    }

    /**
     * @param language {@code es} or {@code en}: the customer's, which says what knowledge files it belongs in
     * @param status   {@code OPEN}, {@code ADDED} or {@code DISMISSED}
     */
    record BotSuggestionResponse(UUID id, UUID conversationId, String language, String question, String answer,
            String status, Instant createdAt) {

        static BotSuggestionResponse from(BotSuggestion suggestion) {
            return new BotSuggestionResponse(suggestion.id(), suggestion.conversationId(), suggestion.language().tag(),
                    suggestion.question(), suggestion.answer(), suggestion.status().name(), suggestion.createdAt());
        }
    }

    /**
     * How an agent did with the cases they got in the last {@code days}.
     *
     * @param firstResponseSeconds on average, from getting a case to their first message; {@code null} without any
     * @param handleSeconds        on average, from getting a case to closing it; {@code null} without any closed
     * @param averageScore         of the surveys about them, from 1 to 5; {@code null} if none was answered
     */
    record AgentMetricsResponse(int days, long attended, long resolved, Double firstResponseSeconds,
            Double handleSeconds, long surveys, Double averageScore) {

        static AgentMetricsResponse from(int days, AgentStats stats) {
            return new AgentMetricsResponse(days, stats.attended(), stats.resolved(), stats.firstResponseSeconds(),
                    stats.handleSeconds(), stats.surveys(), stats.averageScore());
        }
    }

    // Named apart from the money transfer's request: the API's documentation keeps one schema per simple name
    record CaseTransferRequest(@NotNull UUID agentId) {
    }

    record SavedReplyResponse(UUID id, String title, String text, Instant createdAt) {

        static SavedReplyResponse from(SavedReply reply) {
            return new SavedReplyResponse(reply.id(), reply.title(), reply.text(), reply.createdAt());
        }

        static List<SavedReplyResponse> from(List<SavedReply> replies) {
            return replies.stream().map(SavedReplyResponse::from).toList();
        }
    }

    record SavedReplyRequest(
            @NotBlank @Size(max = SavedReply.MAX_TITLE)
            @Pattern(regexp = "[^<>\\p{Cntrl}]*", message = "must not contain <, > or control characters")
            String title,
            @NotBlank @Size(max = SavedReply.MAX_TEXT)
            @Pattern(regexp = ConversationResponses.MESSAGE_TEXT, message = ConversationResponses.MESSAGE_TEXT_ERROR)
            String text) {
    }
}
