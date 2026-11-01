package com.irvinsegura.wallet.accounts.web;

import com.irvinsegura.wallet.accounts.application.WalletAdministrationService;
import com.irvinsegura.wallet.accounts.web.AdminWalletController.AdminWalletResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * The wallets of any user, next to the identity module's {@code /api/v1/admin/users/{userId}}. A user that does not
 * exist simply has no wallets. The ADMIN role is enforced for all of {@code /api/v1/admin/**}.
 */
@RestController
@SecurityRequirement(name = "bearerAuth")
class AdminUserWalletController {

    private final WalletAdministrationService administrationService;

    AdminUserWalletController(WalletAdministrationService administrationService) {
        this.administrationService = administrationService;
    }

    @GetMapping("/api/v1/admin/users/{userId}/wallets")
    List<AdminWalletResponse> list(@PathVariable UUID userId) {
        return administrationService.listWallets(userId).stream().map(AdminWalletResponse::from).toList();
    }
}
