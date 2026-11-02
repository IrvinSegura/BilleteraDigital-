package com.irvinsegura.wallet.notifications.application;

import com.irvinsegura.wallet.accounts.BankAccountAdded;
import com.irvinsegura.wallet.accounts.WalletStatusChanged;
import com.irvinsegura.wallet.accounts.Wallets;
import com.irvinsegura.wallet.identity.PasswordChanged;
import com.irvinsegura.wallet.identity.UserContact;
import com.irvinsegura.wallet.identity.UserDirectory;
import com.irvinsegura.wallet.identity.UserLockedOut;
import com.irvinsegura.wallet.identity.UserRegistered;
import com.irvinsegura.wallet.identity.UserStatusChanged;
import com.irvinsegura.wallet.notifications.domain.model.NotificationMessages;
import com.irvinsegura.wallet.shared.domain.Money;
import com.irvinsegura.wallet.transfers.DepositCompleted;
import com.irvinsegura.wallet.transfers.DepositRejected;
import com.irvinsegura.wallet.transfers.TransferCompleted;
import com.irvinsegura.wallet.transfers.WithdrawalCompleted;
import com.irvinsegura.wallet.transfers.WithdrawalFailed;
import java.util.Currency;
import java.util.UUID;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Turns other modules' events into notifications. Like the audit log, each listener runs after the publishing
 * transaction commits, in its own transaction, and the event stays in the outbox until it succeeds.
 */
@Component
class NotificationEventListener {

    // Used if a user cannot be found, which should never happen because users are never deleted
    private static final String SOMEONE = "another Digital Wallet user";

    private final NotificationService notifications;
    private final UserDirectory users;
    private final Wallets wallets;

    NotificationEventListener(NotificationService notifications, UserDirectory users, Wallets wallets) {
        this.notifications = notifications;
        this.users = users;
        this.wallets = wallets;
    }

    @ApplicationModuleListener
    void on(UserRegistered event) {
        // The administrator is provisioned from configuration and has no use for a welcome
        if ("USER".equals(event.role())) {
            notifications.deliver(NotificationMessages.welcome(event.userId(), event.occurredAt()));
        }
    }

    @ApplicationModuleListener
    void on(UserLockedOut event) {
        notifications.deliver(NotificationMessages.accountLocked(event.userId(), event.lockedUntil(),
                event.occurredAt()));
    }

    // Also emailed: if someone else changed it, the owner learns it from a channel the attacker does not control
    @ApplicationModuleListener
    void on(PasswordChanged event) {
        notifications.deliver(NotificationMessages.passwordChanged(event.userId(), "RESET".equals(event.method()),
                event.occurredAt()));
    }

    @ApplicationModuleListener
    void on(UserStatusChanged event) {
        notifications.deliver("DISABLED".equals(event.status())
                ? NotificationMessages.accountDisabled(event.userId(), event.occurredAt())
                : NotificationMessages.accountEnabled(event.userId(), event.occurredAt()));
    }

    @ApplicationModuleListener
    void on(DepositCompleted event) {
        notifications.deliver(NotificationMessages.depositReceived(event.depositId(), event.userId(),
                Money.ofMinor(event.amountMinor(), event.currency()), event.occurredAt()));
    }

    @ApplicationModuleListener
    void on(DepositRejected event) {
        notifications.deliver(NotificationMessages.depositRejected(event.depositId(), event.userId(),
                Money.ofMinor(event.amountMinor(), event.currency()), event.occurredAt()));
    }

    @ApplicationModuleListener
    void on(WithdrawalCompleted event) {
        notifications.deliver(NotificationMessages.withdrawalSent(event.withdrawalId(), event.userId(),
                Money.ofMinor(event.amountMinor(), event.currency()), event.occurredAt()));
    }

    @ApplicationModuleListener
    void on(WithdrawalFailed event) {
        notifications.deliver(NotificationMessages.withdrawalFailed(event.withdrawalId(), event.userId(),
                Money.ofMinor(event.amountMinor(), event.currency()), event.occurredAt()));
    }

    @ApplicationModuleListener
    void on(TransferCompleted event) {
        Money amount = Money.ofMinor(event.amountMinor(), event.currency());
        String recipientEmail = users.findContact(event.recipientId()).map(UserContact::email).orElse(SOMEONE);
        String senderName = users.findContact(event.senderId()).map(UserContact::fullName).orElse(SOMEONE);
        notifications.deliver(NotificationMessages.transferSent(event.transferId(), event.senderId(), recipientEmail,
                amount, event.occurredAt()));
        notifications.deliver(NotificationMessages.transferReceived(event.transferId(), event.recipientId(),
                senderName, amount, event.occurredAt()));
    }

    @ApplicationModuleListener
    void on(BankAccountAdded event) {
        notifications.deliver(NotificationMessages.bankAccountAdded(event.bankAccountId(), event.ownerId(),
                event.clabeLastFour(), event.occurredAt()));
    }

    @ApplicationModuleListener
    void on(WalletStatusChanged event) {
        Currency currency = wallets.getOwnedWallet(event.walletId(), event.ownerId()).currency();
        UUID walletId = event.walletId();
        notifications.deliver("FROZEN".equals(event.status())
                ? NotificationMessages.walletFrozen(walletId, event.ownerId(), currency, event.occurredAt())
                : NotificationMessages.walletUnfrozen(walletId, event.ownerId(), currency, event.occurredAt()));
    }
}
