package com.irvinsegura.wallet.accounts.web;

import com.irvinsegura.wallet.accounts.application.AdminWalletView;
import com.irvinsegura.wallet.accounts.application.WalletAdministrationService;
import com.irvinsegura.wallet.shared.web.MoneyResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Any wallet, regardless of owner. The ADMIN role is enforced for all of {@code /api/v1/admin/**}.
 */
@RestController
@RequestMapping("/api/v1/admin/wallets")
@SecurityRequirement(name = "bearerAuth")
class AdminWalletController {

    private final WalletAdministrationService administrationService;

    AdminWalletController(WalletAdministrationService administrationService) {
        this.administrationService = administrationService;
    }

    @GetMapping("/{walletId}")
    AdminWalletResponse get(@PathVariable UUID walletId) {
        return AdminWalletResponse.from(administrationService.getWallet(walletId));
    }

    @PostMapping("/{walletId}/freeze")
    AdminWalletResponse freeze(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID walletId,
            @Valid @RequestBody StatusChangeRequest request) {
        return AdminWalletResponse.from(administrationService.freeze(walletId, adminId(jwt), request.reason()));
    }

    @PostMapping("/{walletId}/unfreeze")
    AdminWalletResponse unfreeze(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID walletId,
            @Valid @RequestBody StatusChangeRequest request) {
        return AdminWalletResponse.from(administrationService.unfreeze(walletId, adminId(jwt), request.reason()));
    }

    private static UUID adminId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    // A reason is mandatory: it is what the audit log shows when someone asks why their money was blocked
    record StatusChangeRequest(@NotBlank @Size(max = 200) String reason) {
    }

    record AdminWalletResponse(UUID id, UUID ownerId, MoneyResponse balance, String status, Instant createdAt) {

        static AdminWalletResponse from(AdminWalletView wallet) {
            return new AdminWalletResponse(wallet.id(), wallet.ownerId(), MoneyResponse.from(wallet.balance()),
                    wallet.status(), wallet.createdAt());
        }
    }
}
