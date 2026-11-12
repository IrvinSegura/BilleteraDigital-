package com.irvinsegura.wallet.notifications.domain.model;

import com.irvinsegura.wallet.shared.domain.Money;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Currency;
import java.util.HashMap;
import java.util.Map;
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

    private static Map<String, String> money(Money amount) {
        return Map.of("amount", amount.toDecimal().toPlainString(), "currency", amount.currency().getCurrencyCode());
    }

    private static Map<String, String> money(Money amount, String counterpartKey, String counterpart) {
        return Map.of("amount", amount.toDecimal().toPlainString(), "currency", amount.currency().getCurrencyCode(),
                counterpartKey, counterpart);
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
                null, at, Map.of("until", lockedUntil.toString()));
    }

    /**
     * @param reset whether the user reset a forgotten password through an email link, instead of changing it
     */
    public static Notification passwordChanged(UUID userId, boolean reset, Instant at) {
        return Notification.create(NotificationType.PASSWORD_CHANGED, userId + "@" + at, userId,
                reset ? "Your password was reset" : "Your password was changed",
                ("Your Digital Wallet password was %s and every other device was signed out. If it wasn't you, "
                        + "reset your password right away and contact support.").formatted(reset ? "reset" : "changed"),
                null, at, Map.of("variant", reset ? "reset" : "changed"));
    }

    public static Notification twoFactorEnabled(UUID userId, Instant at) {
        return Notification.create(NotificationType.TWO_FACTOR_ENABLED, userId + "@" + at, userId,
                "Two-factor authentication is on",
                "Signing in now also asks for a code from your authenticator app. Keep your recovery codes somewhere "
                        + "safe: they are the way back in if you lose your phone.",
                null, at);
    }

    /**
     * @param bySupport whether support turned it off, after the user lost their phone
     */
    public static Notification twoFactorDisabled(UUID userId, boolean bySupport, Instant at) {
        return Notification.create(NotificationType.TWO_FACTOR_DISABLED, userId + "@" + at, userId,
                "Two-factor authentication is off",
                (bySupport ? "Our support team turned off two-factor authentication on your account, as you asked. "
                        : "Two-factor authentication was turned off on your account. ")
                        + "If it wasn't you, change your password right away and contact support.",
                null, at, Map.of("variant", bySupport ? "support" : "self"));
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
                "Deposit received", "%s was added to your wallet.".formatted(amount), depositId, at, money(amount));
    }

    // The rule that refused the money is not named: a freeze or a disable is explained by its own notification
    public static Notification depositRejected(UUID depositId, UUID userId, Money amount, Instant at) {
        return Notification.create(NotificationType.DEPOSIT_REJECTED, depositId.toString(), userId,
                "Deposit refunded",
                ("Your payment of %s went through, but your wallet cannot receive money right now, so it was not "
                        + "added. The payment is being refunded to your card.").formatted(amount),
                depositId, at, money(amount));
    }

    /**
     * Also emailed: if someone else added it, the owner learns it from a channel the attacker does not control.
     */
    public static Notification bankAccountAdded(UUID bankAccountId, UUID userId, String clabeLastFour, Instant at) {
        return Notification.create(NotificationType.BANK_ACCOUNT_ADDED, bankAccountId.toString(), userId,
                "Bank account added",
                ("The bank account ending in %s was added and can receive your withdrawals once its waiting period "
                        + "ends. If you did not add it, remove it and change your password right away.")
                        .formatted(clabeLastFour),
                null, at, Map.of("lastFour", clabeLastFour));
    }

    /**
     * Also emailed: if someone else removed it, the owner learns it before a withdrawal goes astray.
     */
    public static Notification bankAccountRemoved(UUID bankAccountId, UUID userId, String clabeLastFour, Instant at) {
        return Notification.create(NotificationType.BANK_ACCOUNT_REMOVED, bankAccountId + "@removed", userId,
                "Bank account removed",
                ("The bank account ending in %s was removed and can no longer receive your withdrawals. If you did "
                        + "not remove it, change your password right away and contact support.")
                        .formatted(clabeLastFour),
                null, at, Map.of("lastFour", clabeLastFour));
    }

    /**
     * Also emailed: the phone is how support confirms who is calling, so a change the owner did not make must reach
     * them.
     */
    public static Notification phoneChanged(UUID userId, Instant at) {
        return Notification.create(NotificationType.PHONE_CHANGED, userId + "@" + at, userId,
                "Phone number changed",
                "The phone number on your account was changed. If it wasn't you, change your password right away "
                        + "and contact support.",
                null, at);
    }

    public static Notification accountUnlocked(UUID userId, Instant at) {
        return Notification.create(NotificationType.ACCOUNT_UNLOCKED, userId + "@" + at, userId,
                "Sign-in unblocked",
                "Our support team unblocked signing in to your account. You can sign in again with your password.",
                null, at);
    }

    /**
     * @param count how many sessions were ended
     */
    public static Notification supportCaseAssigned(UUID conversationId, UUID agentId, Instant at) {
        return Notification.create(NotificationType.SUPPORT_CASE_ASSIGNED, conversationId + "@" + at, agentId,
                "New support case", "A user is waiting for you. Open the case to see everything said with the bot.",
                conversationId, at);
    }

    public static Notification supportAgentJoined(UUID conversationId, UUID userId, Instant at) {
        return Notification.create(NotificationType.SUPPORT_AGENT_JOINED, conversationId + "@" + at, userId,
                "Support is with you", "An agent has joined your conversation and has everything you told the bot.",
                conversationId, at);
    }

    // At most one an hour for each conversation: someone in the chat reads the replies there, and a conversation has many
    public static Notification supportAgentReplied(UUID conversationId, UUID userId, Instant at) {
        return Notification.create(NotificationType.SUPPORT_AGENT_REPLIED,
                conversationId + "@" + at.truncatedTo(ChronoUnit.HOURS), userId, "Support replied",
                "You have a new message from support.", conversationId, at);
    }

    public static Notification supportCaseResolved(UUID conversationId, UUID userId, Instant at) {
        return Notification.create(NotificationType.SUPPORT_CASE_RESOLVED, conversationId.toString(), userId,
                "Your support case was closed", "Tell us how it went: it only takes a moment.", conversationId, at);
    }

    /**
     * @param resolution what the administrator did, in their words
     */
    public static Notification supportTicketResolved(UUID ticketId, UUID userId, String resolution, Instant at) {
        return Notification.create(NotificationType.SUPPORT_TICKET_RESOLVED, ticketId.toString(), userId,
                "Your case was settled", resolution, ticketId, at, Map.of("resolution", resolution));
    }

    /**
     * Tells the agent who escalated a ticket how the administrators settled it, whatever case they attend now.
     */
    public static Notification supportEscalationResolved(UUID ticketId, UUID agentId, String resolution, Instant at) {
        return Notification.create(NotificationType.SUPPORT_ESCALATION_RESOLVED, ticketId.toString(), agentId,
                "A ticket you escalated was settled", resolution, ticketId, at, Map.of("resolution", resolution));
    }

    /**
     * @param credit whether money went into the wallet; otherwise it was taken out
     * @param reason what the administrator wrote for the customer
     */
    public static Notification walletAdjusted(UUID adjustmentId, UUID userId, boolean credit, Money amount,
            String reason, Instant at) {
        Map<String, String> params = new HashMap<>(money(amount));
        params.put("direction", credit ? "CREDIT" : "DEBIT");
        params.put("reason", reason);
        return Notification.create(NotificationType.WALLET_ADJUSTED, adjustmentId.toString(), userId,
                credit ? "Money added to your wallet" : "Money taken from your wallet",
                (credit ? "We added %s to your wallet: %s" : "We took %s from your wallet: %s").formatted(amount, reason),
                adjustmentId, at, params);
    }

    public static Notification identityVerified(UUID verificationId, UUID userId, Instant at) {
        return Notification.create(NotificationType.IDENTITY_VERIFIED, verificationId.toString(), userId,
                "Your identity is verified",
                "We reviewed your INE and confirmed your identity. You can now withdraw money to your bank accounts.",
                verificationId, at);
    }

    /**
     * @param reason why it was rejected, as the administrator wrote it for the customer
     */
    public static Notification identityRejected(UUID verificationId, UUID userId, String reason, Instant at) {
        return Notification.create(NotificationType.IDENTITY_REJECTED, verificationId.toString(), userId,
                "We could not verify your identity", "We reviewed your INE and could not verify it: " + reason,
                verificationId, at, Map.of("reason", reason));
    }

    public static Notification sessionsRevoked(UUID userId, int count, Instant at) {
        return Notification.create(NotificationType.SESSIONS_REVOKED, userId + "@" + at, userId,
                "Sessions signed out",
                "%d of your sessions %s signed out. If it wasn't you, change your password right away."
                        .formatted(count, count == 1 ? "was" : "were"),
                null, at, Map.of("count", String.valueOf(count)));
    }

    public static Notification withdrawalSent(UUID withdrawalId, UUID userId, Money amount, Instant at) {
        return Notification.create(NotificationType.WITHDRAWAL_SENT, withdrawalId.toString(), userId,
                "Withdrawal sent", "%s was sent to your bank account.".formatted(amount), withdrawalId, at, money(amount));
    }

    public static Notification withdrawalFailed(UUID withdrawalId, UUID userId, Money amount, Instant at) {
        return Notification.create(NotificationType.WITHDRAWAL_FAILED, withdrawalId.toString(), userId,
                "Withdrawal returned",
                ("Your bank could not receive your withdrawal of %s, so the money is back in your wallet. Check your "
                        + "bank details or contact support.").formatted(amount),
                withdrawalId, at, money(amount));
    }

    public static Notification transferSent(UUID transferId, UUID senderId, String recipientEmail, Money amount,
            Instant at) {
        return Notification.create(NotificationType.TRANSFER_SENT, transferId.toString(), senderId, "Transfer sent",
                "You sent %s to %s.".formatted(amount, recipientEmail), transferId, at,
                money(amount, "recipient", recipientEmail));
    }

    public static Notification transferReceived(UUID transferId, UUID recipientId, String senderName, Money amount,
            Instant at) {
        return Notification.create(NotificationType.TRANSFER_RECEIVED, transferId.toString(), recipientId,
                "Money received", "%s sent you %s.".formatted(senderName, amount), transferId, at,
                money(amount, "sender", senderName));
    }

    public static Notification walletFrozen(UUID walletId, UUID ownerId, Currency currency, Instant at) {
        return Notification.create(NotificationType.WALLET_FROZEN, walletId + "@" + at, ownerId, "Wallet frozen",
                ("Your %s wallet was frozen: money cannot move in or out of it until our team reviews it. "
                        + "Contact support if you have questions.").formatted(currency.getCurrencyCode()),
                walletId, at, Map.of("currency", currency.getCurrencyCode()));
    }

    public static Notification walletUnfrozen(UUID walletId, UUID ownerId, Currency currency, Instant at) {
        return Notification.create(NotificationType.WALLET_UNFROZEN, walletId + "@" + at, ownerId,
                "Wallet active again",
                "Your %s wallet can send and receive money again.".formatted(currency.getCurrencyCode()),
                walletId, at, Map.of("currency", currency.getCurrencyCode()));
    }
}
