package com.irvinsegura.wallet.payments.web;

import com.irvinsegura.wallet.payments.application.ProviderBalanceService;
import com.irvinsegura.wallet.payments.domain.model.BalanceTransaction;
import com.irvinsegura.wallet.payments.domain.model.ProviderTransaction;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
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
     * Newest first.
     */
    @GetMapping("/provider-transactions")
    List<ProviderTransactionResponse> transactions(
            @RequestParam(defaultValue = "UNMATCHED") ProviderTransaction.Status status,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
        return providerBalanceService.transactionsWithStatus(status, limit).stream()
                .map(ProviderTransactionResponse::from)
                .toList();
    }

    /**
     * Provider balance minus the clearing account, per currency, as of the last sync.
     */
    @GetMapping("/provider-balance-differences")
    Map<String, Long> balanceDifferences() {
        return providerBalanceService.balanceDifferences();
    }

    // Minor units on purpose: these are raw provider figures for operators, not amounts shown to users
    record ProviderTransactionResponse(String provider, String id, String type, String providerType,
            long amountMinor, long feeMinor, String currency, String providerPaymentId, String status, String note,
            Instant createdAt, Instant settledAt) {

        static ProviderTransactionResponse from(ProviderTransaction row) {
            BalanceTransaction transaction = row.transaction();
            return new ProviderTransactionResponse(row.provider(), transaction.id(), transaction.type().name(),
                    transaction.providerType(), transaction.amountMinor(), transaction.feeMinor(),
                    transaction.currency(), transaction.providerPaymentId(), row.status().name(), row.note(),
                    transaction.createdAt(), row.settledAt());
        }
    }
}
