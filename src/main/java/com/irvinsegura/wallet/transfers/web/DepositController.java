package com.irvinsegura.wallet.transfers.web;

import com.irvinsegura.wallet.shared.web.MoneyResponse;
import com.irvinsegura.wallet.transfers.application.DepositCommand;
import com.irvinsegura.wallet.transfers.application.DepositReceipt;
import com.irvinsegura.wallet.transfers.application.DepositService;
import com.irvinsegura.wallet.transfers.application.Idempotent;
import com.irvinsegura.wallet.transfers.domain.model.DepositStatus;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SecurityRequirement(name = "bearerAuth")
class DepositController {

    private final DepositService depositService;

    DepositController(DepositService depositService) {
        this.depositService = depositService;
    }

    /**
     * 201 when the money is already in the wallet, 202 while the payer still has to complete the checkout at
     * {@code checkoutUrl}; a replay of a deposit that ended without money answers 200 with its status.
     */
    @PostMapping("/api/v1/wallets/{walletId}/deposits")
    ResponseEntity<DepositResponse> deposit(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID walletId,
            @RequestHeader(IdempotencyHeaders.KEY)
            @Pattern(regexp = IdempotencyHeaders.KEY_PATTERN, message = IdempotencyHeaders.KEY_MESSAGE) String key,
            @Valid @RequestBody DepositRequest request) {
        Idempotent<DepositReceipt> result = depositService.deposit(
                new DepositCommand(UUID.fromString(jwt.getSubject()), key, walletId, request.amount()));
        return ResponseEntity.status(statusFor(result.value().status()))
                .header(IdempotencyHeaders.REPLAYED, String.valueOf(result.replayed()))
                .body(DepositResponse.from(result.value()));
    }

    // Polled by the client app after the payer comes back from the checkout
    @GetMapping("/api/v1/wallets/{walletId}/deposits/{depositId}")
    DepositResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID walletId,
            @PathVariable UUID depositId) {
        return DepositResponse.from(
                depositService.getDeposit(UUID.fromString(jwt.getSubject()), walletId, depositId));
    }

    private static HttpStatus statusFor(DepositStatus status) {
        return switch (status) {
            case COMPLETED -> HttpStatus.CREATED;
            case PENDING -> HttpStatus.ACCEPTED;
            case EXPIRED, FAILED, REJECTED -> HttpStatus.OK;
        };
    }

    // The currency's own decimal rules are checked in the domain; this only bounds the payload
    record DepositRequest(@NotNull @Positive @Digits(integer = 12, fraction = 4) BigDecimal amount) {
    }

    /**
     * {@code balance} only once completed, {@code checkoutUrl} only while pending, {@code rejectionReason} only when
     * rejected.
     */
    record DepositResponse(UUID id, DepositStatus status, MoneyResponse amount, MoneyResponse balance,
            String checkoutUrl, Instant checkoutExpiresAt, String rejectionReason, Instant createdAt,
            Instant closedAt) {

        static DepositResponse from(DepositReceipt receipt) {
            return new DepositResponse(receipt.id(), receipt.status(), MoneyResponse.from(receipt.amount()),
                    receipt.balance() == null ? null : MoneyResponse.from(receipt.balance()), receipt.checkoutUrl(),
                    receipt.checkoutExpiresAt(), receipt.rejectionReason(), receipt.createdAt(), receipt.closedAt());
        }
    }
}
