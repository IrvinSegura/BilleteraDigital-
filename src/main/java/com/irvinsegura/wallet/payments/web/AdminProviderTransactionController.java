package com.irvinsegura.wallet.payments.web;

import com.irvinsegura.wallet.payments.application.ProviderBalanceService;
import com.irvinsegura.wallet.payments.domain.model.BalanceTransaction;
import com.irvinsegura.wallet.payments.domain.model.ProviderBalanceSnapshot;
import com.irvinsegura.wallet.payments.domain.model.ProviderTransaction;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
 * What the payment provider's balance did and whether the ledger follows it. Unmatched transactions need a person,
 * e.g. a dispute or a refund made from the provider's dashboard. The ADMIN role is enforced for all of
 * {@code /api/v1/admin/**}.
 */
@RestController
@RequestMapping("/api/v1/admin/payments")
@SecurityRequirement(name = "bearerAuth")
class AdminProviderTransactionController {

    private final ProviderBalanceService providerBalanceService;

    AdminProviderTransactionController(ProviderBalanceService providerBalanceService) {
        this.providerBalanceService = providerBalanceService;
    }

    /**
     * Newest first; without a status, every status.
     */
    @GetMapping("/provider-transactions")
    List<ProviderTransactionResponse> transactions(
            @RequestParam(required = false) ProviderTransaction.Status status,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
        return providerBalanceService.transactions(status, limit).stream()
                .map(ProviderTransactionResponse::from)
                .toList();
    }

    /**
     * How many movements wait for attention, for the administrators' home page.
     */
    @GetMapping("/provider-transactions/counts")
    ProviderTransactionCountsResponse counts() {
        return new ProviderTransactionCountsResponse(
                providerBalanceService.count(ProviderTransaction.Status.UNMATCHED),
                providerBalanceService.count(ProviderTransaction.Status.PENDING));
    }

    /**
     * Closes an unmatched movement with what the administrator concluded. Audited; posts nothing to the ledger.
     */
    @PostMapping("/provider-transactions/{provider}/{transactionId}/resolve")
    ProviderTransactionResponse resolve(@AuthenticationPrincipal Jwt jwt, @PathVariable String provider,
            @PathVariable String transactionId, @Valid @RequestBody ResolveRequest request) {
        return ProviderTransactionResponse.from(providerBalanceService.resolve(provider, transactionId,
                UUID.fromString(jwt.getSubject()), request.reason()));
    }

    /**
     * The provider's balance against the clearing account, per currency, as of the last sync.
     */
    @GetMapping("/provider-balance-differences")
    ProviderBalanceStatusResponse balanceDifferences() {
        return new ProviderBalanceStatusResponse(providerBalanceService.providerName(),
                providerBalanceService.providerHoldsFunds(),
                providerBalanceService.balanceComparisons().stream().map(BalanceComparisonResponse::from).toList());
    }

    record ResolveRequest(@NotBlank @Size(max = 200) String reason) {
    }

    /**
     * @param unmatched what the application could not tell, waiting for a person
     * @param pending   waiting to be recorded, usually for their payment's webhook
     */
    record ProviderTransactionCountsResponse(long unmatched, long pending) {
    }

    /**
     * @param holdsFunds whether this provider holds money at all; the simulated one does not, so it has nothing to
     *                   compare
     */
    record ProviderBalanceStatusResponse(String provider, boolean holdsFunds,
            List<BalanceComparisonResponse> comparisons) {
    }

    record BalanceComparisonResponse(String currency, long providerBalanceMinor, long recordedBalanceMinor,
            long differenceMinor, Instant comparedAt) {

        static BalanceComparisonResponse from(ProviderBalanceSnapshot snapshot) {
            return new BalanceComparisonResponse(snapshot.currency(), snapshot.providerBalanceMinor(),
                    snapshot.recordedBalanceMinor(), snapshot.differenceMinor(), snapshot.comparedAt());
        }
    }

    // Minor units on purpose: these are raw provider figures for operators, not amounts shown to users
    record ProviderTransactionResponse(String provider, String id, String type, String providerType,
            long amountMinor, long feeMinor, String currency, String providerPaymentId, String status, String note,
            Instant createdAt, Instant settledAt, UUID resolvedBy, String resolutionReason, Instant resolvedAt) {

        static ProviderTransactionResponse from(ProviderTransaction row) {
            BalanceTransaction transaction = row.transaction();
            ProviderTransaction.Resolution resolution = row.resolution();
            return new ProviderTransactionResponse(row.provider(), transaction.id(), transaction.type().name(),
                    transaction.providerType(), transaction.amountMinor(), transaction.feeMinor(),
                    transaction.currency(), transaction.providerPaymentId(), row.status().name(), row.note(),
                    transaction.createdAt(), row.settledAt(), resolution == null ? null : resolution.resolvedBy(),
                    resolution == null ? null : resolution.reason(), resolution == null ? null : resolution.resolvedAt());
        }
    }
}
