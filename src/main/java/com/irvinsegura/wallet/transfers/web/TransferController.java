package com.irvinsegura.wallet.transfers.web;

import com.irvinsegura.wallet.shared.web.MoneyResponse;
import com.irvinsegura.wallet.transfers.application.Idempotent;
import com.irvinsegura.wallet.transfers.application.TransferCommand;
import com.irvinsegura.wallet.transfers.application.TransferService;
import com.irvinsegura.wallet.transfers.application.TransferView;
import com.irvinsegura.wallet.transfers.domain.model.Transfer;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transfers")
@SecurityRequirement(name = "bearerAuth")
class TransferController {

    private final TransferService transferService;

    TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    ResponseEntity<TransferResponse> transfer(@AuthenticationPrincipal Jwt jwt,
            @RequestHeader(IdempotencyHeaders.KEY)
            @Pattern(regexp = IdempotencyHeaders.KEY_PATTERN, message = IdempotencyHeaders.KEY_MESSAGE) String key,
            @Valid @RequestBody TransferRequest request) {
        Idempotent<TransferView> result = transferService.transfer(new TransferCommand(userId(jwt), key,
                request.sourceWalletId(), request.recipientEmail(), request.amount(), request.description()));
        // A replay answers exactly like the original request, plus the replay header
        return ResponseEntity.created(URI.create("/api/v1/transfers/" + result.value().id()))
                .header(IdempotencyHeaders.REPLAYED, String.valueOf(result.replayed()))
                .body(TransferResponse.from(result.value()));
    }

    @GetMapping("/{transferId}")
    TransferResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID transferId) {
        return TransferResponse.from(transferService.getTransfer(transferId, userId(jwt)));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    record TransferRequest(
            @NotNull UUID sourceWalletId,
            @NotBlank @Email @Size(max = 254) String recipientEmail,
            @NotNull @Positive @Digits(integer = 12, fraction = 4) BigDecimal amount,
            @Size(max = Transfer.MAX_DESCRIPTION_LENGTH) String description) {
    }

    record TransferResponse(UUID id, UUID sourceWalletId, String recipientEmail, MoneyResponse amount,
            String description, Instant createdAt) {

        static TransferResponse from(TransferView transfer) {
            return new TransferResponse(transfer.id(), transfer.sourceWalletId(), transfer.recipientEmail(),
                    MoneyResponse.from(transfer.amount()), transfer.description(), transfer.createdAt());
        }
    }
}
