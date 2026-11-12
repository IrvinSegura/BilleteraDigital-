package com.irvinsegura.wallet.support.web;

import com.irvinsegura.wallet.shared.web.MoneyResponse;
import com.irvinsegura.wallet.support.application.SupportTicketService.TicketPage;
import com.irvinsegura.wallet.support.application.SupportTicketService.TicketView;
import com.irvinsegura.wallet.support.domain.model.SupportTicket;
import com.irvinsegura.wallet.support.domain.model.TicketCategory;
import com.irvinsegura.wallet.transfers.CustomerOperations.OperationBrief;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * What the agent and the administrators read and send about tickets.
 */
final class TicketResponses {

    private TicketResponses() {
    }

    record OperationResponse(String kind, UUID id, String status, MoneyResponse amount, String reason,
            Instant createdAt, Instant closedAt) {

        static OperationResponse from(OperationBrief operation) {
            return operation == null ? null : new OperationResponse(operation.kind(), operation.id(),
                    operation.status(), MoneyResponse.from(operation.amount()), operation.reason(),
                    operation.createdAt(), operation.closedAt());
        }

        static List<OperationResponse> from(List<OperationBrief> operations) {
            return operations.stream().map(OperationResponse::from).toList();
        }
    }

    /**
     * @param customerName  only for administrators; {@code null} for the agent, who has the case
     * @param customerEmail only on one ticket for administrators
     * @param category      {@code OPERATION}, or what about the account it is
     * @param operationKind {@code null} unless it is about an operation, as is {@code operationId}
     * @param operation     the operation as it is now; only on one ticket
     */
    record TicketResponse(UUID id, UUID conversationId, UUID userId, String status, String category,
            String operationKind, UUID operationId, OperationResponse operation, String description, String openedByName,
            String customerName, String customerEmail, Instant createdAt, String resolution, String resolvedByName,
            Instant resolvedAt) {

        static TicketResponse from(TicketView view) {
            SupportTicket ticket = view.ticket();
            return new TicketResponse(ticket.id(), ticket.conversationId(), ticket.userId(), ticket.status().name(),
                    ticket.category().name(), ticket.operationKind(), ticket.operationId(), OperationResponse.from(view.operation()),
                    ticket.description(), view.openedByName(), view.customerName(), view.customerEmail(),
                    ticket.createdAt(), ticket.resolution(), view.resolvedByName(), ticket.resolvedAt());
        }

        static List<TicketResponse> from(List<TicketView> views) {
            return views.stream().map(TicketResponse::from).toList();
        }
    }

    record TicketPageResponse(List<TicketResponse> items, int page, int size, boolean hasNext, long open) {

        static TicketPageResponse from(TicketPage page) {
            return new TicketPageResponse(TicketResponse.from(page.items()), page.page(), page.size(), page.hasNext(),
                    page.open());
        }
    }

    /**
     * @param category    what it is about; {@code OPERATION} when left out
     * @param operationId the operation, for {@code OPERATION} only
     */
    record OpenTicketRequest(TicketCategory category, UUID operationId,
            @NotBlank @Size(max = SupportTicket.MAX_DESCRIPTION)
            @Pattern(regexp = ConversationResponses.MESSAGE_TEXT, message = ConversationResponses.MESSAGE_TEXT_ERROR)
            String description) {

        TicketCategory categoryOrDefault() {
            return category == null ? TicketCategory.OPERATION : category;
        }

        // An operation for an operation ticket, and none for the rest
        @AssertTrue(message = "operationId is required for OPERATION tickets and not allowed for the rest")
        boolean isOperationConsistent() {
            return (categoryOrDefault() == TicketCategory.OPERATION) == (operationId != null);
        }
    }

    record ResolveTicketRequest(@NotBlank @Size(max = SupportTicket.MAX_RESOLUTION)
            @Pattern(regexp = ConversationResponses.MESSAGE_TEXT, message = ConversationResponses.MESSAGE_TEXT_ERROR)
            String resolution) {
    }
}
