package com.irvinsegura.wallet.audit.application;

import com.irvinsegura.wallet.accounts.WalletOpened;
import com.irvinsegura.wallet.accounts.WalletStatusChanged;
import com.irvinsegura.wallet.audit.domain.model.AuditEntry;
import com.irvinsegura.wallet.audit.domain.port.AuditLogRepository;
import com.irvinsegura.wallet.identity.EmailVerified;
import com.irvinsegura.wallet.identity.PasswordChanged;
import com.irvinsegura.wallet.identity.PasswordResetRequested;
import com.irvinsegura.wallet.identity.UserLockedOut;
import com.irvinsegura.wallet.identity.UserRegistered;
import com.irvinsegura.wallet.identity.UserStatusChanged;
import com.irvinsegura.wallet.identity.UserUnlocked;
import com.irvinsegura.wallet.shared.domain.Money;
import com.irvinsegura.wallet.transfers.DepositCompleted;
import com.irvinsegura.wallet.transfers.DepositRejected;
import com.irvinsegura.wallet.transfers.TransferCompleted;
import com.irvinsegura.wallet.transfers.WithdrawalCompleted;
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
    void on(UserUnlocked event) {
        auditLog.append(AuditEntry.of("USER_UNLOCKED", event.userId() + "@" + event.occurredAt(), event.occurredAt(),
                event.unlockedBy(), USER, event.userId(), null));
    }

    @ApplicationModuleListener
    void on(UserStatusChanged event) {
        String action = "DISABLED".equals(event.status()) ? "USER_DISABLED" : "USER_ENABLED";
        auditLog.append(AuditEntry.of(action, event.userId() + "@" + event.occurredAt(), event.occurredAt(),
                event.changedBy(), USER, event.userId(), "reason: " + event.reason()));
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

    @ApplicationModuleListener
    void on(WithdrawalCompleted event) {
        auditLog.append(AuditEntry.of("WITHDRAWAL_COMPLETED", event.withdrawalId().toString(), event.occurredAt(),
                event.userId(), WALLET, event.walletId(),
                "withdrawal %s: %s".formatted(event.withdrawalId(), money(event.amountMinor(), event.currency()))));
    }

    @ApplicationModuleListener
    void on(TransferCompleted event) {
        auditLog.append(AuditEntry.of("TRANSFER_COMPLETED", event.transferId().toString(), event.occurredAt(),
                event.senderId(), WALLET, event.sourceWalletId(),
                "transfer %s: %s to wallet %s".formatted(event.transferId(),
                        money(event.amountMinor(), event.currency()), event.destinationWalletId())));
    }

    private static String money(long minorUnits, String currency) {
        return Money.ofMinor(minorUnits, currency).toString();
    }
}
