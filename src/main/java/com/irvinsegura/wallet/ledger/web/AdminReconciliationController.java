package com.irvinsegura.wallet.ledger.web;

import com.irvinsegura.wallet.ledger.application.ReconciliationService;
import com.irvinsegura.wallet.ledger.domain.model.Discrepancy;
import com.irvinsegura.wallet.ledger.domain.model.ReconciliationRun;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ledger reconciliation results, and a way to run one on demand (e.g. right after fixing an incident). The ADMIN
 * role is enforced for all of {@code /api/v1/admin/**}.
 */
@RestController
@RequestMapping("/api/v1/admin/reconciliations")
@SecurityRequirement(name = "bearerAuth")
class AdminReconciliationController {

    private final ReconciliationService reconciliationService;

    AdminReconciliationController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @PostMapping
    ResponseEntity<ReconciliationResponse> reconcile(@AuthenticationPrincipal Jwt jwt) {
        ReconciliationRun run = reconciliationService.reconcile(ReconciliationRun.Trigger.MANUAL,
                UUID.fromString(jwt.getSubject()));
        return ResponseEntity.created(URI.create("/api/v1/admin/reconciliations?limit=1"))
                .body(ReconciliationResponse.from(run));
    }

    /**
     * Newest first.
     */
    @GetMapping
    List<ReconciliationResponse> recent(@RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return reconciliationService.recentRuns(limit).stream().map(ReconciliationResponse::from).toList();
    }

    record ReconciliationResponse(UUID id, String status, String trigger, UUID triggeredBy, Instant startedAt,
            Instant finishedAt, long accountsChecked, long entriesChecked, List<DiscrepancyResponse> discrepancies) {

        static ReconciliationResponse from(ReconciliationRun run) {
            return new ReconciliationResponse(run.id(), run.isConsistent() ? "CONSISTENT" : "DISCREPANCIES_FOUND",
                    run.trigger().name(), run.triggeredBy(), run.startedAt(), run.finishedAt(),
                    run.accountsChecked(), run.entriesChecked(),
                    run.discrepancies().stream().map(DiscrepancyResponse::from).toList());
        }
    }

    // Minor units on purpose: these are raw ledger figures for operators, not amounts shown to users
    record DiscrepancyResponse(String kind, UUID subjectId, String currency, long expectedMinor, long actualMinor) {

        static DiscrepancyResponse from(Discrepancy discrepancy) {
            return new DiscrepancyResponse(discrepancy.kind().name(), discrepancy.subjectId(),
                    discrepancy.currency(), discrepancy.expected(), discrepancy.actual());
        }
    }
}
