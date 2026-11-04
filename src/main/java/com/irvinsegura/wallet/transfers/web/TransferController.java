package com.irvinsegura.wallet.transfers.web;

import com.irvinsegura.wallet.shared.web.MoneyResponse;
import com.irvinsegura.wallet.shared.web.PlainText;
import com.irvinsegura.wallet.transfers.application.Idempotent;
import com.irvinsegura.wallet.transfers.application.RecipientView;
import com.irvinsegura.wallet.transfers.application.TransferCommand;
import com.irvinsegura.wallet.transfers.application.TransferService;
import com.irvinsegura.wallet.transfers.application.TransferView;
import com.irvinsegura.wallet.transfers.domain.model.Transfer;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.Currency;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

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
                request.sourceWalletId(), request.recipientEmail(), request.amount(), request.description(),
                request.twoFactorCode(), request.currentPassword()));
        // A replay answers exactly like the original request, plus the replay header
        return ResponseEntity.created(URI.create("/api/v1/transfers/" + result.value().id()))
                .header(IdempotencyHeaders.REPLAYED, String.valueOf(result.replayed()))
                .body(TransferResponse.from(result.value()));
    }

    @GetMapping("/{transferId}")
    TransferResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID transferId) {
        return TransferResponse.from(transferService.getTransfer(transferId, userId(jwt)));
    }

    /**
     * The people the user has sent money to, the most recently paid first.
     */
    @GetMapping("/recipients")
    List<RecipientResponse> recentRecipients(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "8") @Min(1) @Max(20) int limit) {
        return transferService.recentRecipients(userId(jwt), limit).stream().map(RecipientResponse::from).toList();
    }

    /**
     * Who a transfer to {@code email} in {@code currency} would reach, with a shortened name, so the sender can
     * check it before confirming. Answers {@code 422 RECIPIENT_NOT_FOUND} like the transfer itself would.
     */
    @GetMapping("/recipient")
    RecipientResponse findRecipient(@AuthenticationPrincipal Jwt jwt,
            @RequestParam @NotBlank @Email @Size(max = 254) String email,
            @RequestParam @Pattern(regexp = "[A-Z]{3}", message = "must be an ISO 4217 code") String currency) {
        Currency parsed;
        try {
            parsed = Currency.getInstance(currency);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown currency");
        }
        return RecipientResponse.from(transferService.findRecipient(userId(jwt), email, parsed));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    /**
     * @param twoFactorCode   step-up proof for accounts with two-factor on; needed for transfers from
     *                        {@code wallet.transfers.step-up-exempt-below} up and for the first one to a recipient,
     *                        which otherwise answer {@code 403 TWO_FACTOR_REQUIRED}
     * @param currentPassword the same proof for accounts without two-factor ({@code 403
     *                        PASSWORD_CONFIRMATION_REQUIRED})
     */
    record TransferRequest(
            @NotNull UUID sourceWalletId,
            @NotBlank @Email @Size(max = 254) String recipientEmail,
            @NotNull @Positive @Digits(integer = 12, fraction = 4) BigDecimal amount,
            @Size(max = Transfer.MAX_DESCRIPTION_LENGTH) @Pattern(regexp = PlainText.NO_MARKUP, message = PlainText.NO_MARKUP_MESSAGE) String description,
            @Size(max = 20) String twoFactorCode,
            @Size(max = 128) String currentPassword) {

        @Override
        public String toString() {
            return "TransferRequest[sourceWalletId=%s, amount=%s, credentials=***]".formatted(sourceWalletId, amount);
        }
    }

    /**
     * @param direction      {@code SENT} or {@code RECEIVED}, from the caller's side
     * @param sourceWalletId only for the sender
     */
    record TransferResponse(UUID id, String direction, UUID sourceWalletId, String senderEmail, String senderName,
            String recipientEmail, String recipientName, MoneyResponse amount, String description,
            Instant createdAt) {

        static TransferResponse from(TransferView transfer) {
            return new TransferResponse(transfer.id(), transfer.direction(), transfer.sourceWalletId(),
                    transfer.senderEmail(), transfer.senderName(), transfer.recipientEmail(),
                    transfer.recipientName(), MoneyResponse.from(transfer.amount()), transfer.description(),
                    transfer.createdAt());
        }
    }

    /**
     * @param displayName first name and initials of the rest
     * @param lastSentAt  {@code null} if the caller never paid them
     */
    record RecipientResponse(String email, String displayName, Instant lastSentAt, long timesSent) {

        static RecipientResponse from(RecipientView recipient) {
            return new RecipientResponse(recipient.email(), recipient.displayName(), recipient.lastSentAt(),
                    recipient.timesSent());
        }
    }
}
