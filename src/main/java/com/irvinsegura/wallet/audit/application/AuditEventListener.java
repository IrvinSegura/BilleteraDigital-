package com.irvinsegura.wallet.audit.application;

import com.irvinsegura.wallet.accounts.BankAccountAdded;
import com.irvinsegura.wallet.accounts.BankAccountRemoved;
import com.irvinsegura.wallet.accounts.WalletOpened;
import com.irvinsegura.wallet.accounts.WalletStatusChanged;
import com.irvinsegura.wallet.audit.domain.model.AuditEntry;
import com.irvinsegura.wallet.audit.domain.port.AuditLogRepository;
import com.irvinsegura.wallet.identity.AccountClosed;
import com.irvinsegura.wallet.identity.EmailChanged;
import com.irvinsegura.wallet.identity.EmailVerified;
import com.irvinsegura.wallet.identity.IdentityVerificationReviewed;
import com.irvinsegura.wallet.identity.IdentityVerificationSubmitted;
import com.irvinsegura.wallet.identity.PasswordChanged;
import com.irvinsegura.wallet.identity.PasswordResetRequested;
import com.irvinsegura.wallet.identity.ProfileUpdated;
import com.irvinsegura.wallet.identity.SessionsRevoked;
import com.irvinsegura.wallet.identity.StaffAccountCreated;
import com.irvinsegura.wallet.identity.TwoFactorDisabled;
import com.irvinsegura.wallet.identity.TwoFactorEnabled;
import com.irvinsegura.wallet.identity.UserLockedOut;
import com.irvinsegura.wallet.identity.UserRegistered;
import com.irvinsegura.wallet.identity.UserSignedIn;
import com.irvinsegura.wallet.identity.UserRoleChanged;
import com.irvinsegura.wallet.identity.UserStatusChanged;
import com.irvinsegura.wallet.identity.UserUnlocked;
import com.irvinsegura.wallet.ledger.CapitalContributed;
import com.irvinsegura.wallet.payments.ProviderTransactionResolved;
import com.irvinsegura.wallet.shared.domain.Money;
import com.irvinsegura.wallet.support.SupportTicketOpened;
import com.irvinsegura.wallet.support.SupportTicketResolved;
import com.irvinsegura.wallet.transfers.AdjustmentApplied;
import com.irvinsegura.wallet.transfers.AdjustmentRejected;
import com.irvinsegura.wallet.transfers.AdjustmentRequested;
import com.irvinsegura.wallet.transfers.DepositCompleted;
import com.irvinsegura.wallet.transfers.DepositRejected;
import com.irvinsegura.wallet.transfers.TransferCompleted;
import com.irvinsegura.wallet.transfers.WithdrawalCompleted;
import com.irvinsegura.wallet.transfers.WithdrawalFailed;
import com.irvinsegura.wallet.transfers.WithdrawalRequested;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Turns other modules' events into audit entries. Each listener runs after the publishing transaction commits, in
 * its own transaction; the event stays in the outbox until it succeeds, so no entry is lost if this fails.
 */
@Component
class AuditEventListener {

    private static final String USER = "USER";
    private static final String WALLET = "WALLET";
    private static final String LEDGER_ENTRY = "LEDGER_ENTRY";
    private static final String PROVIDER_TRANSACTION = "PROVIDER_TRANSACTION";

    private final AuditLogRepository auditLog;

    AuditEventListener(AuditLogRepository auditLog) {
        this.auditLog = auditLog;
    }

    @ApplicationModuleListener
    void on(UserRegistered event) {
        auditLog.append(AuditEntry.of("USER_REGISTERED", event.userId().toString(), event.occurredAt(),
                event.userId(), USER, event.userId(), "role " + event.role()));
    }

    @ApplicationModuleListener
    void on(UserSignedIn event) {
        auditLog.append(AuditEntry.of("USER_SIGNED_IN", event.userId() + "@" + event.occurredAt(), event.occurredAt(),
                event.userId(), USER, event.userId(), null));
    }

    @ApplicationModuleListener
    void on(UserLockedOut event) {
        // No actor: the system locked the account
        auditLog.append(AuditEntry.of("USER_LOCKED_OUT", event.userId() + "@" + event.occurredAt(),
                event.occurredAt(), null, USER, event.userId(), "locked until " + event.lockedUntil()));
    }

    @ApplicationModuleListener
    void on(EmailVerified event) {
        auditLog.append(AuditEntry.of("USER_EMAIL_VERIFIED", event.userId() + "@" + event.occurredAt(),
                event.occurredAt(), event.userId(), USER, event.userId(), null));
    }

    // No actor: anyone can ask for a reset link; a burst of them for one account is worth noticing
    @ApplicationModuleListener
    void on(PasswordResetRequested event) {
        auditLog.append(AuditEntry.of("PASSWORD_RESET_REQUESTED", event.userId() + "@" + event.occurredAt(),
                event.occurredAt(), null, USER, event.userId(), null));
    }

    @ApplicationModuleListener
    void on(PasswordChanged event) {
        String action = "RESET".equals(event.method()) ? "PASSWORD_RESET" : "PASSWORD_CHANGED";
        auditLog.append(AuditEntry.of(action, event.userId() + "@" + event.occurredAt(), event.occurredAt(),
                event.userId(), USER, event.userId(), "all sessions ended"));
    }

    @ApplicationModuleListener
    void on(EmailChanged event) {
        auditLog.append(AuditEntry.of("EMAIL_CHANGED", event.userId() + "@" + event.occurredAt(), event.occurredAt(),
                event.userId(), USER, event.userId(), "all sessions ended"));
    }

    @ApplicationModuleListener
    void on(AccountClosed event) {
        auditLog.append(AuditEntry.of("ACCOUNT_CLOSED", event.userId() + "@" + event.occurredAt(), event.occurredAt(),
                event.userId(), USER, event.userId(), "closed by its owner"));
    }

    @ApplicationModuleListener
    void on(ProfileUpdated event) {
        auditLog.append(AuditEntry.of("PROFILE_UPDATED", event.userId() + "@" + event.occurredAt(),
                event.occurredAt(), event.userId(), USER, event.userId(), String.join(", ", event.fields())));
    }

    @ApplicationModuleListener
    void on(SessionsRevoked event) {
        auditLog.append(AuditEntry.of("SESSIONS_REVOKED", event.userId() + "@" + event.occurredAt(),
                event.occurredAt(), event.userId(), USER, event.userId(), event.count() + " ended"));
    }

    @ApplicationModuleListener
    void on(TwoFactorEnabled event) {
        auditLog.append(AuditEntry.of("TWO_FACTOR_ENABLED", event.userId() + "@" + event.occurredAt(),
                event.occurredAt(), event.userId(), USER, event.userId(), null));
    }

    @ApplicationModuleListener
    void on(TwoFactorDisabled event) {
        auditLog.append(AuditEntry.of("TWO_FACTOR_DISABLED", event.userId() + "@" + event.occurredAt(),
                event.occurredAt(), event.disabledBy(), USER, event.userId(),
                event.reason() == null ? null : "reason: " + event.reason()));
    }

    @ApplicationModuleListener
    void on(UserUnlocked event) {
        auditLog.append(AuditEntry.of("USER_UNLOCKED", event.userId() + "@" + event.occurredAt(), event.occurredAt(),
                event.unlockedBy(), USER, event.userId(), "reason: " + event.reason()));
    }

    @ApplicationModuleListener
    void on(IdentityVerificationSubmitted event) {
        auditLog.append(AuditEntry.of("IDENTITY_VERIFICATION_SUBMITTED", event.verificationId().toString(),
                event.occurredAt(), event.userId(), USER, event.userId(), "document INE"));
    }

    @ApplicationModuleListener
    void on(IdentityVerificationReviewed event) {
        String action = "VERIFIED".equals(event.status()) ? "IDENTITY_VERIFIED" : "IDENTITY_REJECTED";
        auditLog.append(AuditEntry.of(action, event.verificationId() + "@" + event.occurredAt(), event.occurredAt(),
                event.reviewedBy(), USER, event.userId(),
                event.reason() == null ? "approved" : "reason: " + event.reason()));
    }

    @ApplicationModuleListener
    void on(StaffAccountCreated event) {
        auditLog.append(AuditEntry.of("STAFF_ACCOUNT_CREATED", event.userId().toString(), event.occurredAt(),
                event.createdBy(), USER, event.userId(), "role: " + event.role() + ", reason: " + event.reason()));
    }

    @ApplicationModuleListener
    void on(SupportTicketOpened event) {
        auditLog.append(AuditEntry.of("SUPPORT_TICKET_OPENED", event.ticketId().toString(), event.occurredAt(),
                event.agentId(), USER, event.userId(),
                event.operationId() == null ? "ticket " + event.ticketId() + ": " + event.category().toLowerCase()
                        : "ticket " + event.ticketId() + ": " + event.operationKind() + " " + event.operationId()));
    }

    @ApplicationModuleListener
    void on(SupportTicketResolved event) {
        auditLog.append(AuditEntry.of("SUPPORT_TICKET_RESOLVED", event.ticketId() + "@" + event.occurredAt(),
                event.occurredAt(), event.adminId(), USER, event.userId(),
                "ticket " + event.ticketId() + ", resolution: " + event.resolution()));
    }

    @ApplicationModuleListener
    void on(UserRoleChanged event) {
        auditLog.append(AuditEntry.of("USER_ROLE_CHANGED", event.userId() + "@" + event.occurredAt(), event.occurredAt(),
                event.changedBy(), USER, event.userId(), "role: " + event.role() + ", reason: " + event.reason()));
    }

    @ApplicationModuleListener
    void on(UserStatusChanged event) {
        String action = "DISABLED".equals(event.status()) ? "USER_DISABLED" : "USER_ENABLED";
        auditLog.append(AuditEntry.of(action, event.userId() + "@" + event.occurredAt(), event.occurredAt(),
                event.changedBy(), USER, event.userId(), "reason: " + event.reason()));
    }

    // Where withdrawals can go: the trail support follows when money leaves a taken-over account
    @ApplicationModuleListener
    void on(BankAccountAdded event) {
        auditLog.append(AuditEntry.of("BANK_ACCOUNT_ADDED", event.bankAccountId().toString(), event.occurredAt(),
                event.ownerId(), USER, event.ownerId(), "bank account %s: bank %s, CLABE ending %s".formatted(
                        event.bankAccountId(), event.bankCode(), event.clabeLastFour())));
    }

    @ApplicationModuleListener
    void on(BankAccountRemoved event) {
        auditLog.append(AuditEntry.of("BANK_ACCOUNT_REMOVED", event.bankAccountId().toString(), event.occurredAt(),
                event.ownerId(), USER, event.ownerId(), "bank account %s: CLABE ending %s".formatted(
                        event.bankAccountId(), event.clabeLastFour())));
    }

    // Money the platform itself put in; the reference ties it to the bank wire
    @ApplicationModuleListener
    void on(CapitalContributed event) {
        auditLog.append(AuditEntry.of("CAPITAL_CONTRIBUTED", event.journalEntryId().toString(), event.occurredAt(),
                event.contributedBy(), LEDGER_ENTRY, event.journalEntryId(), "%s, reference %s, reason: %s".formatted(
                        money(event.amountMinor(), event.currency()), event.reference(), event.reason())));
    }

    @ApplicationModuleListener
    void on(WalletOpened event) {
        auditLog.append(AuditEntry.of("WALLET_OPENED", event.walletId().toString(), event.occurredAt(),
                event.ownerId(), WALLET, event.walletId(), "currency " + event.currency()));
    }

    @ApplicationModuleListener
    void on(WalletStatusChanged event) {
        String action = "FROZEN".equals(event.status()) ? "WALLET_FROZEN" : "WALLET_UNFROZEN";
        auditLog.append(AuditEntry.of(action, event.walletId() + "@" + event.occurredAt(), event.occurredAt(),
                event.changedBy(), WALLET, event.walletId(), "reason: " + event.reason()));
    }

    @ApplicationModuleListener
    void on(DepositCompleted event) {
        auditLog.append(AuditEntry.of("DEPOSIT_COMPLETED", event.depositId().toString(), event.occurredAt(),
                event.userId(), WALLET, event.walletId(),
                "deposit %s: %s".formatted(event.depositId(), money(event.amountMinor(), event.currency()))));
    }

    // Paid but not credited, so support can answer "where is my money" with the refund under way
    @ApplicationModuleListener
    void on(DepositRejected event) {
        auditLog.append(AuditEntry.of("DEPOSIT_REJECTED", event.depositId().toString(), event.occurredAt(),
                null, WALLET, event.walletId(), "deposit %s: %s refunded, %s".formatted(event.depositId(),
                        money(event.amountMinor(), event.currency()), event.reason())));
    }

    // Money leaving a taken-over account shows up here, before the bank answers
    @ApplicationModuleListener
    void on(WithdrawalRequested event) {
        auditLog.append(AuditEntry.of("WITHDRAWAL_REQUESTED", event.withdrawalId().toString(), event.occurredAt(),
                event.userId(), WALLET, event.walletId(),
                "withdrawal %s: %s".formatted(event.withdrawalId(), money(event.amountMinor(), event.currency()))));
    }

    @ApplicationModuleListener
    void on(WithdrawalCompleted event) {
        auditLog.append(AuditEntry.of("WITHDRAWAL_COMPLETED", event.withdrawalId().toString(), event.occurredAt(),
                event.userId(), WALLET, event.walletId(),
                "withdrawal %s: %s".formatted(event.withdrawalId(), money(event.amountMinor(), event.currency()))));
    }

    // No actor: the bank refused the payout, and the money went back to the wallet
    @ApplicationModuleListener
    void on(AdjustmentRequested event) {
        auditLog.append(AuditEntry.of("ADJUSTMENT_REQUESTED", event.adjustmentId().toString(), event.occurredAt(),
                event.requestedBy(), WALLET, event.walletId(), "adjustment %s: %s %s, reason: %s".formatted(
                        event.adjustmentId(), event.direction().toLowerCase(), money(event.amountMinor(),
                                event.currency()), event.reason())));
    }

    @ApplicationModuleListener
    void on(AdjustmentApplied event) {
        auditLog.append(AuditEntry.of("ADJUSTMENT_APPLIED", event.adjustmentId() + "@applied", event.occurredAt(),
                event.approvedBy(), WALLET, event.walletId(), "adjustment %s: %s %s, reason: %s".formatted(
                        event.adjustmentId(), event.direction().toLowerCase(), money(event.amountMinor(),
                                event.currency()), event.reason())));
    }

    @ApplicationModuleListener
    void on(AdjustmentRejected event) {
        auditLog.append(AuditEntry.of("ADJUSTMENT_REJECTED", event.adjustmentId() + "@rejected", event.occurredAt(),
                event.rejectedBy(), USER, event.userId(), "adjustment %s, reason: %s".formatted(event.adjustmentId(),
                        event.reason())));
    }

    @ApplicationModuleListener
    void on(WithdrawalFailed event) {
        auditLog.append(AuditEntry.of("WITHDRAWAL_FAILED", event.withdrawalId().toString(), event.occurredAt(),
                null, WALLET, event.walletId(), "withdrawal %s: %s returned, %s".formatted(event.withdrawalId(),
                        money(event.amountMinor(), event.currency()), event.reason())));
    }

    @ApplicationModuleListener
    void on(TransferCompleted event) {
        auditLog.append(AuditEntry.of("TRANSFER_COMPLETED", event.transferId().toString(), event.occurredAt(),
                event.senderId(), WALLET, event.sourceWalletId(),
                "transfer %s: %s to wallet %s".formatted(event.transferId(),
                        money(event.amountMinor(), event.currency()), event.destinationWalletId())));
    }

    // The provider's ids are not UUIDs: the target is derived from one, which the details spell out
    @ApplicationModuleListener
    void on(ProviderTransactionResolved event) {
        String key = event.provider() + ":" + event.transactionId();
        auditLog.append(AuditEntry.of("PROVIDER_TRANSACTION_RESOLVED", key, event.occurredAt(), event.resolvedBy(),
                PROVIDER_TRANSACTION, UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)),
                "%s %s, reason: %s".formatted(event.provider(), event.transactionId(), event.reason())));
    }

    private static String money(long minorUnits, String currency) {
        return Money.ofMinor(minorUnits, currency).toString();
    }
}
