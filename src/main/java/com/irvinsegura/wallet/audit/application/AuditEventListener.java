package com.irvinsegura.wallet.audit.application;

import com.irvinsegura.wallet.accounts.WalletOpened;
import com.irvinsegura.wallet.accounts.WalletStatusChanged;
import com.irvinsegura.wallet.audit.domain.model.AuditEntry;
import com.irvinsegura.wallet.audit.domain.port.AuditLogRepository;
import com.irvinsegura.wallet.identity.UserLockedOut;
import com.irvinsegura.wallet.identity.UserRegistered;
import com.irvinsegura.wallet.shared.domain.Money;
import com.irvinsegura.wallet.transfers.DepositCompleted;
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
