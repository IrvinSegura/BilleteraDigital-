package com.irvinsegura.wallet.notifications.domain.model;

import java.util.Set;

/**
 * Which notifications the user also gets by email. Every notification stays in the inbox; only routine money
 * movements can be left out of email. Security notices and failed operations are always sent, since the user may
 * need to act on them.
 *
 * @param moneyIn  deposits and transfers received
 * @param moneyOut transfers sent and withdrawals on their way
 */
public record EmailPreferences(boolean moneyIn, boolean moneyOut) {

    public static final EmailPreferences DEFAULT = new EmailPreferences(true, true);

    private static final Set<NotificationType> MONEY_IN =
            Set.of(NotificationType.DEPOSIT_RECEIVED, NotificationType.TRANSFER_RECEIVED);
    private static final Set<NotificationType> MONEY_OUT =
            Set.of(NotificationType.TRANSFER_SENT, NotificationType.WITHDRAWAL_SENT);

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
