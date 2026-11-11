package com.irvinsegura.wallet.accounts.web;

import com.irvinsegura.wallet.accounts.application.BankAccountService;
import com.irvinsegura.wallet.accounts.application.BankAccountSummary;
import com.irvinsegura.wallet.shared.web.PlainText;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bank accounts (CLABE) the authenticated user withdraws MXN to. The full CLABE is accepted but never returned.
 */
@RestController
@RequestMapping("/api/v1/bank-accounts")
@SecurityRequirement(name = "bearerAuth")
class BankAccountController {

    private final BankAccountService bankAccountService;

    BankAccountController(BankAccountService bankAccountService) {
        this.bankAccountService = bankAccountService;
    }

    @PostMapping
    ResponseEntity<BankAccountResponse> add(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AddBankAccountRequest request) {
        BankAccountSummary bankAccount = bankAccountService.add(userId(jwt), request.clabe(), request.holderName(),
                request.twoFactorCode());
        return ResponseEntity.created(URI.create("/api/v1/bank-accounts/" + bankAccount.id()))
                .body(BankAccountResponse.from(bankAccount));
    }

    @GetMapping
    List<BankAccountResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return bankAccountService.list(userId(jwt)).stream().map(BankAccountResponse::from).toList();
    }

    /**
     * Sets or, with a blank alias, clears the name the owner gives the account.
     */
    @PatchMapping("/{bankAccountId}")
    BankAccountResponse rename(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID bankAccountId,
            @Valid @RequestBody RenameBankAccountRequest request) {
        return BankAccountResponse.from(bankAccountService.rename(userId(jwt), bankAccountId, request.alias()));
    }

    @DeleteMapping("/{bankAccountId}")
    ResponseEntity<Void> remove(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID bankAccountId) {
        bankAccountService.remove(userId(jwt), bankAccountId);
        return ResponseEntity.noContent().build();
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    /**
     * Spaces and dashes are allowed in the CLABE; the domain checks the digits themselves.
     *
     * @param twoFactorCode the authenticator app's code or a recovery code, for users with two-factor
     *                      authentication on
     */
    record AddBankAccountRequest(@NotBlank @Size(max = 30) String clabe,
            @NotBlank @Size(max = 100) @Pattern(regexp = PlainText.NO_MARKUP, message = PlainText.NO_MARKUP_MESSAGE) String holderName, @Size(max = 20) String twoFactorCode) {
    }

    record RenameBankAccountRequest(
            @Size(max = 40) @Pattern(regexp = PlainText.NO_MARKUP, message = PlainText.NO_MARKUP_MESSAGE) String alias) {
    }

    record BankAccountResponse(UUID id, String bankCode, String clabeLastFour, String holderName, String alias,
            Instant createdAt, Instant withdrawalsAvailableFrom) {

        static BankAccountResponse from(BankAccountSummary bankAccount) {
            return new BankAccountResponse(bankAccount.id(), bankAccount.bankCode(), bankAccount.clabeLastFour(),
                    bankAccount.holderName(), bankAccount.alias(), bankAccount.createdAt(),
                    bankAccount.withdrawalsAvailableFrom());
        }
    }
}
