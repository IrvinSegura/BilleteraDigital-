package com.irvinsegura.wallet.transfers.web;

import com.irvinsegura.wallet.shared.web.MoneyResponse;
import com.irvinsegura.wallet.transfers.application.DepositCommand;
import com.irvinsegura.wallet.transfers.application.DepositReceipt;
import com.irvinsegura.wallet.transfers.application.DepositService;
import com.irvinsegura.wallet.transfers.application.Idempotent;
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

    @PostMapping("/api/v1/wallets/{walletId}/deposits")
    ResponseEntity<DepositResponse> deposit(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID walletId,
            @RequestHeader(IdempotencyHeaders.KEY)
            @Pattern(regexp = IdempotencyHeaders.KEY_PATTERN, message = IdempotencyHeaders.KEY_MESSAGE) String key,
            @Valid @RequestBody DepositRequest request) {
        Idempotent<DepositReceipt> result = depositService.deposit(
                new DepositCommand(UUID.fromString(jwt.getSubject()), key, walletId, request.amount()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(IdempotencyHeaders.REPLAYED, String.valueOf(result.replayed()))
                .body(DepositResponse.from(result.value()));
    }

    // The currency's own decimal rules are checked in the domain; this only bounds the payload
    record DepositRequest(@NotNull @Positive @Digits(integer = 12, fraction = 4) BigDecimal amount) {
    }

    record DepositResponse(UUID id, MoneyResponse amount, MoneyResponse balance, Instant createdAt) {

        static DepositResponse from(DepositReceipt receipt) {
            return new DepositResponse(receipt.id(), MoneyResponse.from(receipt.amount()),
                    MoneyResponse.from(receipt.balance()), receipt.createdAt());
        }
    }
}
