package com.irvinsegura.wallet.audit.web;

import com.irvinsegura.wallet.audit.application.AuditLogService;
import com.irvinsegura.wallet.audit.domain.model.AuditEntry;
import com.irvinsegura.wallet.audit.domain.port.AuditLogRepository;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only access to the audit log. The ADMIN role is enforced for all of {@code /api/v1/admin/**}.
 */
@RestController
@RequestMapping("/api/v1/admin/audit-events")
@SecurityRequirement(name = "bearerAuth")
class AdminAuditController {

    private final AuditLogService auditLogService;

    AdminAuditController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    /**
     * Filter by {@code targetId} for a wallet's or user's history, or by {@code actorId} for what someone did.
     */
    @GetMapping
    AuditPageResponse search(@RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) UUID targetId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        AuditLogRepository.AuditSlice slice = auditLogService.search(actorId, targetId, page, size);
        return new AuditPageResponse(slice.items().stream().map(AuditEventResponse::from).toList(), page, size,
                slice.hasNext());
    }

    record AuditEventResponse(UUID id, Instant occurredAt, UUID actorId, String action, String targetType,
            UUID targetId, String details) {

        static AuditEventResponse from(AuditEntry entry) {
            return new AuditEventResponse(entry.id(), entry.occurredAt(), entry.actorId(), entry.action(),
                    entry.targetType(), entry.targetId(), entry.details());
        }
    }

    record AuditPageResponse(List<AuditEventResponse> items, int page, int size, boolean hasNext) {
    }
}
