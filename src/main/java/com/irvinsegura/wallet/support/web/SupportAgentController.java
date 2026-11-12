package com.irvinsegura.wallet.support.web;

import com.irvinsegura.wallet.support.application.SavedReplyService;
import com.irvinsegura.wallet.support.application.SupportAgentService;
import com.irvinsegura.wallet.support.application.SupportAgentService.AgentSummary;
import com.irvinsegura.wallet.support.application.SupportTicketService;
import com.irvinsegura.wallet.support.domain.model.ConversationStatus;
import com.irvinsegura.wallet.support.domain.model.TicketStatus;
import com.irvinsegura.wallet.support.web.AgentResponses.AgentMetricsResponse;
import com.irvinsegura.wallet.support.web.AgentResponses.AvailabilityRequest;
import com.irvinsegura.wallet.support.web.AgentResponses.BotSuggestionRequest;
import com.irvinsegura.wallet.support.web.AgentResponses.BotSuggestionResponse;
import com.irvinsegura.wallet.support.web.AgentResponses.ColleagueResponse;
import com.irvinsegura.wallet.support.web.AgentResponses.NoteRequest;
import com.irvinsegura.wallet.support.web.AgentResponses.CaseResolveRequest;
import com.irvinsegura.wallet.support.web.AgentResponses.CustomerStandingResponse;
import com.irvinsegura.wallet.support.web.AgentResponses.SavedReplyRequest;
import com.irvinsegura.wallet.support.web.AgentResponses.SavedReplyResponse;
import com.irvinsegura.wallet.support.web.AgentResponses.CaseTransferRequest;
import com.irvinsegura.wallet.support.web.ConversationResponses.ConversationPageResponse;
import com.irvinsegura.wallet.support.web.ConversationResponses.ConversationResponse;
import com.irvinsegura.wallet.support.web.ConversationResponses.ConversationSummaryResponse;
import com.irvinsegura.wallet.support.web.ConversationResponses.MessageRequest;
import com.irvinsegura.wallet.support.web.ConversationResponses.NoteResponse;
import com.irvinsegura.wallet.support.web.TicketResponses.OpenTicketRequest;
import com.irvinsegura.wallet.support.web.TicketResponses.OperationResponse;
import com.irvinsegura.wallet.support.web.TicketResponses.TicketPageResponse;
import com.irvinsegura.wallet.support.web.TicketResponses.TicketResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * The cases of the agent who is signed in. The SUPPORT role is enforced for all of {@code /api/v1/support/agent/**}.
 */
@RestController
@RequestMapping("/api/v1/support/agent")
@SecurityRequirement(name = "bearerAuth")
class SupportAgentController {

    private final SupportAgentService agents;
    private final SupportTicketService tickets;
    private final SavedReplyService savedReplies;

    SupportAgentController(SupportAgentService agents, SupportTicketService tickets, SavedReplyService savedReplies) {
        this.agents = agents;
        this.tickets = tickets;
        this.savedReplies = savedReplies;
    }

    /**
     * The case being attended, if any, whether new ones come to the agent, and how the queue is.
     */
    @GetMapping("/summary")
    AgentSummary summary(@AuthenticationPrincipal Jwt jwt) {
        return agents.summary(agentId(jwt));
    }

    /**
     * Pauses new cases, keeping the one being attended, or takes them again.
     */
    @PutMapping("/availability")
    AgentSummary availability(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AvailabilityRequest request) {
        return agents.setAvailability(agentId(jwt), request.available());
    }

    /**
     * How the agent did with the cases they got in the last {@code days}.
     */
    @GetMapping("/metrics")
    AgentMetricsResponse metrics(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int days) {
        return AgentMetricsResponse.from(days, agents.metrics(agentId(jwt), days));
    }

    /**
     * The agent's cases: the open one first, then the most recent.
     *
     * @param status {@code ASSIGNED} for the open one, {@code RESOLVED} for the closed ones; any if left out
     * @param q      the customer's name or the case's folio
     */
    @GetMapping("/conversations")
    ConversationPageResponse list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) ConversationStatus status,
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ConversationPageResponse.from(agents.list(agentId(jwt), status, q, page, size));
    }

    /**
     * One of the agent's cases with everything said in it, the bot's part included.
     */
    @GetMapping("/conversations/{conversationId}")
    ConversationResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID conversationId) {
        return ConversationResponse.from(agents.get(agentId(jwt), conversationId));
    }

    @PostMapping("/conversations/{conversationId}/messages")
    ConversationResponse reply(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID conversationId,
            @Valid @RequestBody MessageRequest request) {
        return ConversationResponse.from(agents.reply(agentId(jwt), conversationId, request.text()));
    }

    /**
     * Sends a file with the same rules as the user's.
     */
    @PostMapping(path = "/conversations/{conversationId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ConversationResponse attach(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID conversationId,
            @RequestParam MultipartFile file,
            @RequestParam(required = false) @Size(max = ConversationResponses.MAX_TEXT_LENGTH)
            @Pattern(regexp = ConversationResponses.MESSAGE_TEXT, message = ConversationResponses.MESSAGE_TEXT_ERROR)
            String text) throws IOException {
        return ConversationResponse.from(agents.attach(agentId(jwt), conversationId, file.getOriginalFilename(),
                file.getBytes(), text));
    }

    /**
     * Closes the case with how it ended, and the next one in the queue comes to this agent.
     */
    @PostMapping("/conversations/{conversationId}/resolve")
    ConversationResponse resolve(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID conversationId,
            @Valid @RequestBody(required = false) CaseResolveRequest request) {
        CaseResolveRequest reason = request == null ? new CaseResolveRequest(null) : request;
        return ConversationResponse.from(agents.resolve(agentId(jwt), conversationId, reason.reasonOrDefault()));
    }

    /**
     * The customer's earlier cases with people, the most recent first.
     */
    @GetMapping("/conversations/{conversationId}/history")
    List<ConversationSummaryResponse> history(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID conversationId) {
        return ConversationSummaryResponse.from(agents.history(agentId(jwt), conversationId));
    }

    /**
     * The notes the team left on the case, which the customer never reads.
     */
    @GetMapping("/conversations/{conversationId}/notes")
    List<NoteResponse> notes(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID conversationId) {
        return NoteResponse.from(agents.notes(agentId(jwt), conversationId));
    }

    @PostMapping("/conversations/{conversationId}/notes")
    ResponseEntity<List<NoteResponse>> addNote(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID conversationId,
            @Valid @RequestBody NoteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(NoteResponse.from(agents.addNote(agentId(jwt), conversationId, request.text())));
    }

    /**
     * Proposes to the administrators that the bot answer this question by itself, as the agent did.
     */
    @PostMapping("/conversations/{conversationId}/bot-suggestions")
    ResponseEntity<BotSuggestionResponse> suggestToBot(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID conversationId, @Valid @RequestBody BotSuggestionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(BotSuggestionResponse.from(
                agents.suggestToBot(agentId(jwt), conversationId, request.question(), request.answer())));
    }

    /**
     * The customer's recent operations, read only, while the case is open: what the agent needs to understand a
     * problem with money, never a balance or a bank account.
     */
    @GetMapping("/conversations/{conversationId}/operations")
    List<OperationResponse> operations(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID conversationId) {
        return OperationResponse.from(tickets.operations(agentId(jwt), conversationId));
    }

    @GetMapping("/conversations/{conversationId}/tickets")
    List<TicketResponse> tickets(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID conversationId) {
        return TicketResponse.from(tickets.forCase(agentId(jwt), conversationId));
    }

    /**
     * Where the customer's account stands (enabled, locked, email, INE, frozen wallets) while the case is open: the
     * usual answer to why money can't leave. No contact or document data.
     */
    @GetMapping("/conversations/{conversationId}/customer")
    CustomerStandingResponse customer(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID conversationId) {
        return CustomerStandingResponse.from(tickets.standing(agentId(jwt), conversationId));
    }

    /**
     * Gives the case back to the queue for a colleague; the next one waiting may come to this agent.
     */
    @PostMapping("/conversations/{conversationId}/release")
    ResponseEntity<Void> release(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID conversationId) {
        agents.release(agentId(jwt), conversationId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Passes the case to a colleague who isn't attending one.
     */
    @PostMapping("/conversations/{conversationId}/transfer")
    ResponseEntity<Void> transfer(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID conversationId,
            @Valid @RequestBody CaseTransferRequest request) {
        agents.transfer(agentId(jwt), conversationId, request.agentId());
        return ResponseEntity.noContent().build();
    }

    /**
     * The other active agents and whether each is attending a case.
     */
    @GetMapping("/colleagues")
    List<ColleagueResponse> colleagues(@AuthenticationPrincipal Jwt jwt) {
        return ColleagueResponse.from(agents.colleagues(agentId(jwt)));
    }

    /**
     * What the agent escalated, from any of their cases: open ones the oldest first.
     *
     * @param status {@code OPEN} or {@code RESOLVED}; any if left out
     */
    @GetMapping("/tickets")
    TicketPageResponse myTickets(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) TicketStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return TicketPageResponse.from(tickets.openedBy(agentId(jwt), status, page, size));
    }

    @GetMapping("/replies")
    List<SavedReplyResponse> replies(@AuthenticationPrincipal Jwt jwt) {
        return SavedReplyResponse.from(savedReplies.list(agentId(jwt)));
    }

    @PostMapping("/replies")
    ResponseEntity<SavedReplyResponse> saveReply(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SavedReplyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(SavedReplyResponse.from(
                savedReplies.save(agentId(jwt), request.title(), request.text())));
    }

    @DeleteMapping("/replies/{replyId}")
    ResponseEntity<Void> deleteReply(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID replyId) {
        savedReplies.delete(agentId(jwt), replyId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Passes a customer's problem to the administrators: about one of their operations, or about the account itself
     * (a sign-in lock, an INE review). Agents never move money nor change accounts.
     */
    @PostMapping("/conversations/{conversationId}/tickets")
    ResponseEntity<TicketResponse> openTicket(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID conversationId,
            @Valid @RequestBody OpenTicketRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(TicketResponse.from(
                tickets.open(agentId(jwt), conversationId, request.categoryOrDefault(), request.operationId(),
                        request.description())));
    }

    private static UUID agentId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
