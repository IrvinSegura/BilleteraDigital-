package com.irvinsegura.wallet.transfers.web;

import com.irvinsegura.wallet.shared.web.MoneyResponse;
import com.irvinsegura.wallet.transfers.application.OperationAdministrationService;
import com.irvinsegura.wallet.transfers.domain.model.Deposit;
import com.irvinsegura.wallet.transfers.domain.model.DepositStatus;
import com.irvinsegura.wallet.transfers.domain.model.OperationSuggestion;
import com.irvinsegura.wallet.transfers.domain.model.OperationDailyTotal;
import com.irvinsegura.wallet.transfers.domain.model.OperationTotal;
import com.irvinsegura.wallet.transfers.domain.model.PendingOperation;
import com.irvinsegura.wallet.transfers.domain.model.Transfer;
import com.irvinsegura.wallet.transfers.domain.model.Withdrawal;
import com.irvinsegura.wallet.transfers.domain.model.WithdrawalStatus;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Any deposit, withdrawal or transfer, regardless of who made it. The ADMIN role is enforced for all of
 * {@code /api/v1/admin/**}. {@code journalEntryId} links each one to its ledger entry.
 */
@RestController
@RequestMapping("/api/v1/admin")
@SecurityRequirement(name = "bearerAuth")
class AdminOperationController {

    private final OperationAdministrationService administrationService;

    AdminOperationController(OperationAdministrationService administrationService) {
        this.administrationService = administrationService;
    }

    /**
     * Up to six operations whose id starts with {@code q}, the newest first, as the administrator types.
     */
    @GetMapping("/operations/suggestions")
    List<OperationSuggestionResponse> suggestions(
            @RequestParam @Pattern(regexp = "[0-9a-fA-F-]{1,36}", message = "must be the start of an id") String q,
            @RequestParam(required = false)
            @Pattern(regexp = "transfer|deposit|withdrawal", message = "must be a kind of operation") String kind) {
        return administrationService.suggest(q, kind).stream().map(OperationSuggestionResponse::from).toList();
    }

    /**
     * Deposits and withdrawals still pending, the oldest first: the ones most likely stuck.
     */
    @GetMapping("/operations/pending")
    List<PendingOperationResponse> pending(
            @RequestParam(required = false)
            @Pattern(regexp = "deposit|withdrawal", message = "must be deposit or withdrawal") String kind,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
        return administrationService.pending(kind, limit).stream().map(PendingOperationResponse::from).toList();
    }

    /**
     * Completed deposits, paid withdrawals and transfers of the last {@code days} days, by kind and currency.
     */
    @GetMapping("/operations/totals")
    List<OperationTotalResponse> totals(@RequestParam(defaultValue = "7") @Min(1) @Max(90) int days) {
        return administrationService.totals(days).stream().map(OperationTotalResponse::from).toList();
    }

    /**
     * What finished each day, for the charts of the administration home. Days with nothing have no row.
     */
    @GetMapping("/operations/daily")
    List<OperationDailyResponse> daily(@RequestParam(defaultValue = "30") @Min(1) @Max(180) int days) {
        return administrationService.dailyTotals(days).stream().map(OperationDailyResponse::from).toList();
    }

    @GetMapping("/transfers/{transferId}")
    AdminTransferResponse transfer(@PathVariable UUID transferId) {
        return AdminTransferResponse.from(administrationService.getTransfer(transferId));
    }

    @GetMapping("/deposits/{depositId}")
    AdminDepositResponse deposit(@PathVariable UUID depositId) {
        return AdminDepositResponse.from(administrationService.getDeposit(depositId));
    }

    @GetMapping("/withdrawals/{withdrawalId}")
    AdminWithdrawalResponse withdrawal(@PathVariable UUID withdrawalId) {
        return AdminWithdrawalResponse.from(administrationService.getWithdrawal(withdrawalId));
    }

    record PendingOperationResponse(String kind, UUID id, UUID userId, UUID walletId, MoneyResponse amount,
            Instant createdAt) {

        static PendingOperationResponse from(PendingOperation operation) {
            return new PendingOperationResponse(operation.kind(), operation.id(), operation.userId(),
                    operation.walletId(), MoneyResponse.from(operation.amount()), operation.createdAt());
        }
    }

    record OperationDailyResponse(LocalDate date, String kind, long count, MoneyResponse amount) {

        static OperationDailyResponse from(OperationDailyTotal total) {
            return new OperationDailyResponse(total.date(), total.kind(), total.count(),
                    MoneyResponse.from(total.amount()));
        }
    }

    record OperationTotalResponse(String kind, long count, MoneyResponse amount) {

        static OperationTotalResponse from(OperationTotal total) {
            return new OperationTotalResponse(total.kind(), total.count(), MoneyResponse.from(total.amount()));
        }
    }

    record OperationSuggestionResponse(String kind, UUID id, MoneyResponse amount, Instant createdAt) {

        static OperationSuggestionResponse from(OperationSuggestion suggestion) {
            return new OperationSuggestionResponse(suggestion.kind(), suggestion.id(),
                    MoneyResponse.from(suggestion.amount()), suggestion.createdAt());
        }
    }

    record AdminTransferResponse(UUID id, UUID senderId, UUID sourceWalletId, UUID recipientId,
            String recipientEmail, UUID destinationWalletId, MoneyResponse amount, String description,
            UUID journalEntryId, Instant createdAt) {

        static AdminTransferResponse from(Transfer transfer) {
            return new AdminTransferResponse(transfer.id(), transfer.senderId(), transfer.sourceWalletId(),
                    transfer.recipientId(), transfer.recipientEmail(), transfer.destinationWalletId(),
                    MoneyResponse.from(transfer.amount()), transfer.description(), transfer.journalEntryId(),
                    transfer.createdAt());
        }
    }

    // Ledger fields are null until the deposit is completed; the payment shares the deposit's id
    record AdminDepositResponse(UUID id, UUID userId, UUID walletId, DepositStatus status, MoneyResponse amount,
            MoneyResponse balanceAfter, UUID journalEntryId, String rejectionReason, Instant createdAt,
            Instant closedAt) {

        static AdminDepositResponse from(Deposit deposit) {
            return new AdminDepositResponse(deposit.id(), deposit.userId(), deposit.walletId(), deposit.status(),
                    MoneyResponse.from(deposit.amount()),
                    deposit.balanceAfter() == null ? null : MoneyResponse.from(deposit.balanceAfter()),
                    deposit.journalEntryId(), deposit.rejectionReason(), deposit.createdAt(), deposit.closedAt());
        }
    }

    /**
     * {@code journalEntryId} took the money from the wallet; {@code settlementEntryId} paid it out or gave it back,
     * and {@code providerReference} finds the payout at the provider.
     */
    record AdminWithdrawalResponse(UUID id, UUID userId, UUID walletId, UUID bankAccountId, WithdrawalStatus status,
            MoneyResponse amount, MoneyResponse balanceAfter, UUID journalEntryId, UUID settlementEntryId,
            String providerReference, String failureReason, Instant createdAt, Instant closedAt) {

        static AdminWithdrawalResponse from(Withdrawal withdrawal) {
            return new AdminWithdrawalResponse(withdrawal.id(), withdrawal.userId(), withdrawal.walletId(),
                    withdrawal.bankAccountId(), withdrawal.status(), MoneyResponse.from(withdrawal.amount()),
                    MoneyResponse.from(withdrawal.balanceAfter()), withdrawal.journalEntryId(),
                    withdrawal.settlementEntryId(), withdrawal.providerReference(), withdrawal.failureReason(),
                    withdrawal.createdAt(), withdrawal.closedAt());
        }
    }
}
