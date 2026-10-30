package com.irvinsegura.wallet.accounts.web;

import com.irvinsegura.wallet.accounts.application.WalletService;
import com.irvinsegura.wallet.accounts.application.WalletSummary;
import com.irvinsegura.wallet.ledger.AccountMovement;
import com.irvinsegura.wallet.ledger.MovementPage;
import com.irvinsegura.wallet.shared.web.MoneyResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
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
 * Wallets of the authenticated user. The owner always comes from the token, so users only ever see their own.
 */
@RestController
@RequestMapping("/api/v1/wallets")
@SecurityRequirement(name = "bearerAuth")
class WalletController {

    private final WalletService walletService;

    WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping
    ResponseEntity<WalletResponse> open(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody OpenWalletRequest request) {
        WalletSummary wallet = walletService.openWallet(userId(jwt), request.currency());
        return ResponseEntity.created(URI.create("/api/v1/wallets/" + wallet.id()))
                .body(WalletResponse.from(wallet));
    }

    @GetMapping
    List<WalletResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return walletService.listWallets(userId(jwt)).stream().map(WalletResponse::from).toList();
    }

    @GetMapping("/{walletId}")
    WalletResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID walletId) {
        return WalletResponse.from(walletService.getWallet(walletId, userId(jwt)));
    }

    @GetMapping("/{walletId}/movements")
    MovementPageResponse movements(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID walletId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return MovementPageResponse.from(walletService.movements(walletId, userId(jwt), page, size));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    record OpenWalletRequest(@NotBlank @Pattern(regexp = "[A-Z]{3}", message = "must be an ISO 4217 code")
            String currency) {
    }

    record WalletResponse(UUID id, MoneyResponse balance, String status, Instant createdAt) {

        static WalletResponse from(WalletSummary wallet) {
            return new WalletResponse(wallet.id(), MoneyResponse.from(wallet.balance()), wallet.status(),
                    wallet.createdAt());
        }
    }

    record MovementResponse(UUID transactionId, String type, String direction, MoneyResponse amount,
            MoneyResponse balanceAfter, Instant createdAt) {

        static MovementResponse from(AccountMovement movement) {
            return new MovementResponse(movement.journalEntryId(), movement.type(), movement.direction().name(),
                    MoneyResponse.from(movement.amount()), MoneyResponse.from(movement.balanceAfter()),
                    movement.postedAt());
        }
    }

    record MovementPageResponse(List<MovementResponse> items, int page, int size, boolean hasNext) {

        static MovementPageResponse from(MovementPage page) {
            return new MovementPageResponse(page.items().stream().map(MovementResponse::from).toList(),
                    page.page(), page.size(), page.hasNext());
        }
    }
}
