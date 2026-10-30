package com.irvinsegura.wallet.transfers.web;

import com.irvinsegura.wallet.shared.web.MoneyResponse;
import com.irvinsegura.wallet.transfers.application.DepositCommand;
import com.irvinsegura.wallet.transfers.application.DepositReceipt;
import com.irvinsegura.wallet.transfers.application.DepositService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SecurityRequirement(name = "bearerAuth")
class DepositController {

    private final DepositService depositService;

    DepositController(DepositService depositService) {
        this.depositService = depositService;
    }

    @PostMapping("/api/v1/wallets/{walletId}/deposits")
    @ResponseStatus(HttpStatus.CREATED)
    DepositResponse deposit(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID walletId,
            @Valid @RequestBody DepositRequest request) {
        DepositReceipt receipt = depositService.deposit(
                new DepositCommand(UUID.fromString(jwt.getSubject()), walletId, request.amount()));
        return DepositResponse.from(receipt);
    }

    // The currency's own decimal rules are checked in the domain; this only bounds the payload
    record DepositRequest(@NotNull @Positive @Digits(integer = 12, fraction = 4) BigDecimal amount) {
    }

    record DepositResponse(UUID transactionId, MoneyResponse amount, MoneyResponse balance, Instant createdAt) {

        static DepositResponse from(DepositReceipt receipt) {
            return new DepositResponse(receipt.transactionId(), MoneyResponse.from(receipt.amount()),
                    MoneyResponse.from(receipt.balance()), receipt.createdAt());
        }
    }
}
