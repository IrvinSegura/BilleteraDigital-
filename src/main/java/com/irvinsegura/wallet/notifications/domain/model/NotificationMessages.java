package com.irvinsegura.wallet.notifications.domain.model;

import com.irvinsegura.wallet.shared.domain.Money;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Currency;
import java.util.UUID;

/**
 * The text of every notification, in one place. Messages never include internal details such as an
 * administrator's freeze reason; users only learn what happened to their own account and money.
 */
public final class NotificationMessages {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'")
            .withZone(ZoneOffset.UTC);

    private NotificationMessages() {
    }

    public static Notification welcome(UUID userId, Instant at) {
        return Notification.create(NotificationType.WELCOME, userId.toString(), userId, "Welcome to Digital Wallet",
                "Open a wallet in MXN or USD to start sending and receiving money.", null, at);
    }

    public static Notification accountLocked(UUID userId, Instant lockedUntil, Instant at) {
        return Notification.create(NotificationType.ACCOUNT_LOCKED, userId + "@" + at, userId,
                "Sign-in blocked after failed attempts",
                ("Someone entered a wrong password for your account several times, so signing in is blocked until "
                        + "%s. If it wasn't you, reset your password: that also unblocks your account.")
                        .formatted(TIME.format(lockedUntil)),
                null, at);
    }

    /**
     * @param reset whether the user reset a forgotten password through an email link, instead of changing it
     */
    public static Notification passwordChanged(UUID userId, boolean reset, Instant at) {
        return Notification.create(NotificationType.PASSWORD_CHANGED, userId + "@" + at, userId,
                reset ? "Your password was reset" : "Your password was changed",
                ("Your Digital Wallet password was %s and every other device was signed out. If it wasn't you, "
                        + "reset your password right away and contact support.").formatted(reset ? "reset" : "changed"),
                null, at);
    }

    public static Notification accountDisabled(UUID userId, Instant at) {
        return Notification.create(NotificationType.ACCOUNT_DISABLED, userId + "@" + at, userId,
                "Account disabled",
                "Your account was disabled: you cannot sign in or move money until our team reviews it. "
                        + "Contact support if you have questions.",
                null, at);
    }

    public static Notification accountEnabled(UUID userId, Instant at) {
        return Notification.create(NotificationType.ACCOUNT_ENABLED, userId + "@" + at, userId,
                "Account active again", "You can sign in and move money again.", null, at);
    }

    public static Notification depositReceived(UUID depositId, UUID userId, Money amount, Instant at) {
        return Notification.create(NotificationType.DEPOSIT_RECEIVED, depositId.toString(), userId,
                "Deposit received", "%s was added to your wallet.".formatted(amount), depositId, at);
    }

    public static Notification withdrawalSent(UUID withdrawalId, UUID userId, Money amount, Instant at) {
        return Notification.create(NotificationType.WITHDRAWAL_SENT, withdrawalId.toString(), userId,
                "Withdrawal sent", "%s is on its way to your bank account.".formatted(amount), withdrawalId, at);
    }

    public static Notification transferSent(UUID transferId, UUID senderId, String recipientEmail, Money amount,
            Instant at) {
        return Notification.create(NotificationType.TRANSFER_SENT, transferId.toString(), senderId, "Transfer sent",
                "You sent %s to %s.".formatted(amount, recipientEmail), transferId, at);
    }

    public static Notification transferReceived(UUID transferId, UUID recipientId, String senderName, Money amount,
            Instant at) {
        return Notification.create(NotificationType.TRANSFER_RECEIVED, transferId.toString(), recipientId,
                "Money received", "%s sent you %s.".formatted(senderName, amount), transferId, at);
    }

    public static Notification walletFrozen(UUID walletId, UUID ownerId, Currency currency, Instant at) {
        return Notification.create(NotificationType.WALLET_FROZEN, walletId + "@" + at, ownerId, "Wallet frozen",
                ("Your %s wallet was frozen: money cannot move in or out of it until our team reviews it. "
                        + "Contact support if you have questions.").formatted(currency.getCurrencyCode()),
                walletId, at);
    }

    public static Notification walletUnfrozen(UUID walletId, UUID ownerId, Currency currency, Instant at) {
        return Notification.create(NotificationType.WALLET_UNFROZEN, walletId + "@" + at, ownerId,
                "Wallet active again",
                "Your %s wallet can send and receive money again.".formatted(currency.getCurrencyCode()),
                walletId, at);
    }
}
