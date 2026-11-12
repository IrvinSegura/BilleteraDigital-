package com.irvinsegura.wallet.transfers.web;

import com.irvinsegura.wallet.shared.web.MoneyResponse;
import com.irvinsegura.wallet.transfers.application.AdjustmentService;
import com.irvinsegura.wallet.transfers.application.AdjustmentService.AdjustmentPage;
import com.irvinsegura.wallet.transfers.application.AdjustmentService.AdjustmentView;
import com.irvinsegura.wallet.transfers.domain.model.Adjustment;
import com.irvinsegura.wallet.transfers.domain.model.AdjustmentDirection;
import com.irvinsegura.wallet.transfers.domain.model.AdjustmentStatus;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
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
 * Administrators putting money into or taking it out of a customer's wallet to settle a problem. The ADMIN role,
 * and its second factor, are enforced for all of {@code /api/v1/admin/**}.
 */
@RestController
@Validated
@RequestMapping("/api/v1/admin/adjustments")
@SecurityRequirement(name = "bearerAuth")
class AdminAdjustmentController {

    private final AdjustmentService adjustments;

    AdminAdjustmentController(AdjustmentService adjustments) {
        this.adjustments = adjustments;
    }

    /**
     * Applied at once when within the requester's limit (201, status {@code APPLIED}); otherwise it waits for a
     * second administrator (status {@code PENDING_APPROVAL}).
     */
    @PostMapping
    ResponseEntity<AdjustmentResponse> request(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AdjustmentRequest request) {
        AdjustmentView view = adjustments.request(UUID.fromString(jwt.getSubject()), request.walletId(),
                request.direction(), request.amount(), request.reason(), request.ticketId());
        return ResponseEntity.created(URI.create("/api/v1/admin/adjustments/" + view.adjustment().id()))
                .body(AdjustmentResponse.from(view));
    }

    /**
     * Those waiting for approval come first, oldest first.
     */
    @GetMapping
    AdjustmentPageResponse list(@RequestParam(required = false) AdjustmentStatus status,
            @RequestParam(required = false) UUID ticketId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return AdjustmentPageResponse.from(adjustments.page(status, ticketId, page, size));
    }

    @GetMapping("/{adjustmentId}")
    AdjustmentResponse get(@PathVariable UUID adjustmentId) {
        return AdjustmentResponse.from(adjustments.get(adjustmentId));
    }

    @PostMapping("/{adjustmentId}/approve")
    AdjustmentResponse approve(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID adjustmentId) {
        return AdjustmentResponse.from(adjustments.approve(UUID.fromString(jwt.getSubject()), adjustmentId));
    }

    @PostMapping("/{adjustmentId}/reject")
    AdjustmentResponse reject(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID adjustmentId,
            @Valid @RequestBody RejectAdjustmentRequest request) {
        return AdjustmentResponse.from(adjustments.reject(UUID.fromString(jwt.getSubject()), adjustmentId,
                request.reason()));
    }

    /**
     * @param reason   what happened, as the customer will read it in their notice
     * @param ticketId the support ticket it settles, if any
     */
    record AdjustmentRequest(@NotNull UUID walletId, @NotNull AdjustmentDirection direction,
            @NotNull @Positive @Digits(integer = 12, fraction = 4) BigDecimal amount,
            @NotBlank @Size(max = Adjustment.MAX_REASON) String reason, UUID ticketId) {
    }

    record RejectAdjustmentRequest(@NotBlank @Size(max = Adjustment.MAX_REASON) String reason) {
    }

    /**
     * @param customerEmail  only on a single adjustment
     * @param secondApproval whether a second administrator has to apply it
     */
    record AdjustmentResponse(UUID id, UUID walletId, UUID userId, String customerName, String customerEmail,
            String direction, MoneyResponse amount, String reason, UUID ticketId, String status, boolean secondApproval,
            UUID requestedBy, String requestedByName, Instant requestedAt, String decidedByName, Instant decidedAt,
            String rejectionReason, UUID journalEntryId) {

        static AdjustmentResponse from(AdjustmentView view) {
            Adjustment a = view.adjustment();
            return new AdjustmentResponse(a.id(), a.walletId(), a.userId(), view.customerName(), view.customerEmail(),
                    a.direction().name(), MoneyResponse.from(a.amount()), a.reason(), a.ticketId(), a.status().name(),
                    view.secondApproval(), a.requestedBy(), view.requestedByName(), a.requestedAt(),
                    view.decidedByName(), a.decidedAt(), a.rejectionReason(), a.journalEntryId());
        }
    }

    record AdjustmentPageResponse(List<AdjustmentResponse> items, int page, int size, boolean hasNext, long pending) {

        static AdjustmentPageResponse from(AdjustmentPage page) {
            return new AdjustmentPageResponse(page.items().stream().map(AdjustmentResponse::from).toList(),
                    page.page(), page.size(), page.hasNext(), page.pending());
        }
    }
}
