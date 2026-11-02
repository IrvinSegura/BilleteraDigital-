package com.irvinsegura.wallet.transfers.web;

import com.irvinsegura.wallet.shared.web.MoneyResponse;
import com.irvinsegura.wallet.transfers.application.OperationAdministrationService;
import com.irvinsegura.wallet.transfers.domain.model.Deposit;
import com.irvinsegura.wallet.transfers.domain.model.DepositStatus;
import com.irvinsegura.wallet.transfers.domain.model.Transfer;
import com.irvinsegura.wallet.transfers.domain.model.Withdrawal;
import com.irvinsegura.wallet.transfers.domain.model.WithdrawalStatus;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.time.Instant;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
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
