package com.irvinsegura.wallet.transfers.web;

import com.irvinsegura.wallet.shared.web.MoneyResponse;
import com.irvinsegura.wallet.transfers.application.Idempotent;
import com.irvinsegura.wallet.transfers.application.WithdrawalCommand;
import com.irvinsegura.wallet.transfers.application.WithdrawalReceipt;
import com.irvinsegura.wallet.transfers.application.WithdrawalService;
import com.irvinsegura.wallet.transfers.domain.model.WithdrawalStatus;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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
class WithdrawalController {

    private final WithdrawalService withdrawalService;

    WithdrawalController(WithdrawalService withdrawalService) {
        this.withdrawalService = withdrawalService;
    }

    /**
     * 201 when the bank already confirmed the payout, 202 while it is on its way; the money has left the wallet
     * either way. A replay of a payout the bank refused answers 200 with its status.
     */
    @PostMapping("/api/v1/wallets/{walletId}/withdrawals")
    ResponseEntity<WithdrawalResponse> withdraw(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID walletId,
            @RequestHeader(IdempotencyHeaders.KEY)
            @Pattern(regexp = IdempotencyHeaders.KEY_PATTERN, message = IdempotencyHeaders.KEY_MESSAGE) String key,
            @Valid @RequestBody WithdrawalRequest request) {
        Idempotent<WithdrawalReceipt> result = withdrawalService.withdraw(
                new WithdrawalCommand(UUID.fromString(jwt.getSubject()), key, walletId, request.amount(),
                        request.bankAccountId(), request.twoFactorCode(), request.currentPassword()));
        return ResponseEntity.status(statusFor(result.value().status()))
                .header(IdempotencyHeaders.REPLAYED, String.valueOf(result.replayed()))
                .body(WithdrawalResponse.from(result.value()));
    }

    @GetMapping("/api/v1/wallets/{walletId}/withdrawals/{withdrawalId}")
    WithdrawalResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID walletId,
            @PathVariable UUID withdrawalId) {
        return WithdrawalResponse.from(
                withdrawalService.getWithdrawal(UUID.fromString(jwt.getSubject()), walletId, withdrawalId));
    }

    /**
     * The currencies withdrawals can be made in. A wallet in another one can't withdraw ({@code 422
     * WITHDRAWAL_CURRENCY_NOT_SUPPORTED}): STP, for one, pays only pesos.
     */
    @GetMapping("/api/v1/withdrawals/currencies")
    WithdrawalCurrenciesResponse currencies() {
        return new WithdrawalCurrenciesResponse(withdrawalService.payableCurrencies());
    }

    record WithdrawalCurrenciesResponse(List<String> currencies) {
    }

    private static HttpStatus statusFor(WithdrawalStatus status) {
        return switch (status) {
            case PAID -> HttpStatus.CREATED;
            case PENDING -> HttpStatus.ACCEPTED;
            case FAILED -> HttpStatus.OK;
        };
    }

    /**
     * @param bankAccountId   one of the user's bank accounts; required for MXN wallets, not allowed for USD
     * @param twoFactorCode   step-up proof every withdrawal needs from accounts with two-factor on ({@code 403
     *                        TWO_FACTOR_REQUIRED} without it)
     * @param currentPassword the same proof from accounts without two-factor ({@code 403
     *                        PASSWORD_CONFIRMATION_REQUIRED} without it)
     */
    record WithdrawalRequest(@NotNull @Positive @Digits(integer = 12, fraction = 4) BigDecimal amount,
            UUID bankAccountId,
            @Size(max = 20) String twoFactorCode,
            @Size(max = 128) String currentPassword) {

        @Override
        public String toString() {
            return "WithdrawalRequest[amount=%s, bankAccountId=%s, credentials=***]".formatted(amount, bankAccountId);
        }
    }

    /**
     * {@code balance} is the wallet balance right after the money left it; {@code failureReason} only when failed.
     */
    record WithdrawalResponse(UUID id, WithdrawalStatus status, MoneyResponse amount, MoneyResponse balance,
            UUID bankAccountId, String failureReason, Instant createdAt, Instant closedAt) {

        static WithdrawalResponse from(WithdrawalReceipt receipt) {
            return new WithdrawalResponse(receipt.id(), receipt.status(), MoneyResponse.from(receipt.amount()),
                    MoneyResponse.from(receipt.balance()), receipt.bankAccountId(), receipt.failureReason(),
                    receipt.createdAt(), receipt.closedAt());
        }
    }
}
