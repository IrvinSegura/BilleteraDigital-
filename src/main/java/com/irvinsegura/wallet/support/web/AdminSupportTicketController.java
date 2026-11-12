package com.irvinsegura.wallet.support.web;

import com.irvinsegura.wallet.support.application.SupportTicketService;
import com.irvinsegura.wallet.support.domain.model.TicketStatus;
import com.irvinsegura.wallet.support.web.TicketResponses.ResolveTicketRequest;
import com.irvinsegura.wallet.support.web.TicketResponses.TicketPageResponse;
import com.irvinsegura.wallet.support.web.TicketResponses.TicketResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The tickets support passes to the administrators. The ADMIN role, and its second factor, are enforced for all of
 * {@code /api/v1/admin/**}. Resolving one records what was done; the money itself is fixed with the operations tools.
 */
@RestController
@Validated
@RequestMapping("/api/v1/admin/support/tickets")
@SecurityRequirement(name = "bearerAuth")
class AdminSupportTicketController {

    private final SupportTicketService tickets;

    AdminSupportTicketController(SupportTicketService tickets) {
        this.tickets = tickets;
    }

    /**
     * Open tickets come oldest first, so the one waiting longest is at the top.
     */
    @GetMapping
    TicketPageResponse list(@RequestParam(required = false) TicketStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return TicketPageResponse.from(tickets.page(status, page, size));
    }

    @GetMapping("/{ticketId}")
    TicketResponse get(@PathVariable UUID ticketId) {
        return TicketResponse.from(tickets.get(ticketId));
    }

    @PostMapping("/{ticketId}/resolve")
    TicketResponse resolve(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID ticketId,
            @Valid @RequestBody ResolveTicketRequest request) {
        return TicketResponse.from(tickets.resolve(UUID.fromString(jwt.getSubject()), ticketId, request.resolution()));
    }
}
