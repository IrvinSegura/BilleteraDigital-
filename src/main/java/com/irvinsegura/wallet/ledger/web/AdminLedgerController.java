package com.irvinsegura.wallet.ledger.web;

import com.irvinsegura.wallet.ledger.application.CapitalService;
import com.irvinsegura.wallet.ledger.domain.model.LedgerAccount;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The platform's own accounts: their balances, and capital put into the settlement account to fund payouts. The
 * ADMIN role is enforced for all of {@code /api/v1/admin/**}.
 */
@RestController
@RequestMapping("/api/v1/admin/ledger")
@SecurityRequirement(name = "bearerAuth")
class AdminLedgerController {

    private final CapitalService capitalService;

    AdminLedgerController(CapitalService capitalService) {
        this.capitalService = capitalService;
    }

    @PostMapping("/capital-contributions")
    ResponseEntity<SystemAccountResponse> contribute(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CapitalContributionRequest request) {
        LedgerAccount settlement = capitalService.contribute(UUID.fromString(jwt.getSubject()), request.currency(),
                request.amount(), request.reference(), request.reason());
        return ResponseEntity.created(URI.create("/api/v1/admin/ledger/system-accounts"))
                .body(SystemAccountResponse.from(settlement));
    }

    @GetMapping("/system-accounts")
    List<SystemAccountResponse> systemAccounts() {
        return capitalService.systemAccounts().stream().map(SystemAccountResponse::from).toList();
    }

    /**
     * @param reference the bank reference of the wire that brought the money in; each is recorded once
     */
    record CapitalContributionRequest(@NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
            @NotNull @Positive @Digits(integer = 12, fraction = 4) BigDecimal amount,
            @NotBlank @Size(max = 100) String reference, @NotBlank @Size(max = 200) String reason) {
    }

    // Minor units on purpose: these are raw ledger figures for operators, not amounts shown to users
    record SystemAccountResponse(UUID id, String code, String type, String currency, long balanceMinor) {

        static SystemAccountResponse from(LedgerAccount account) {
            return new SystemAccountResponse(account.id(), account.systemCode(), account.type().name(),
                    account.currency().getCurrencyCode(), account.balance().minorUnits());
        }
    }
}
