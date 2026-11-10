package com.irvinsegura.wallet.notifications.domain.model;

import java.util.Set;

/**
 * Which notifications the user also gets by email, and whether each sign-in is announced by email. Every notification stays in the inbox; only routine money
 * movements can be left out of email. Security notices and failed operations are always sent, since the user may
 * need to act on them.
 *
 * @param moneyIn  deposits and transfers received
 * @param moneyOut transfers sent and withdrawals on their way
 * @param signIns  an email at each successful sign-in, which someone who signs in often may not want. Unlike the
 *                 rest it is never a notification: the inbox would fill up with the user's own sign-ins
 */
public record EmailPreferences(boolean moneyIn, boolean moneyOut, boolean signIns) {

    public static final EmailPreferences DEFAULT = new EmailPreferences(true, true, true);

    private static final Set<NotificationType> MONEY_IN =
            Set.of(NotificationType.DEPOSIT_RECEIVED, NotificationType.TRANSFER_RECEIVED);
    private static final Set<NotificationType> MONEY_OUT =
            Set.of(NotificationType.TRANSFER_SENT, NotificationType.WITHDRAWAL_SENT);

    /**
     * Whether the user can leave this notification out of email.
     */
    public static boolean isOptional(NotificationType type) {
        return MONEY_IN.contains(type) || MONEY_OUT.contains(type);
    }

    public boolean allowsEmail(NotificationType type) {
        if (MONEY_IN.contains(type)) {
            return moneyIn;
        }
        if (MONEY_OUT.contains(type)) {
            return moneyOut;
        }
        return true;
    }
}
