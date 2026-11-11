package com.irvinsegura.wallet.identity;

import java.time.Instant;
import java.util.UUID;

/**
 * Where a customer's account stands, for the support agent attending them: the usual answer to "why can't I withdraw?".
 * No contact data and no document data, only states and dates.
 *
 * @param status                         {@code ACTIVE}, {@code DISABLED} or {@code CLOSED}
 * @param lockedUntil                    until when sign-in is locked after failed attempts; {@code null} if it isn't
 * @param identityStatus                 {@code NONE} if they never sent their INE; otherwise the state of the latest
 *                                       one, or {@code VERIFIED} if any was verified
 * @param identitySubmittedAt            when the latest INE was sent; {@code null} if never
 * @param identityRejectionReason        why the latest INE was turned down; {@code null} unless it was
 * @param identityRequiredForWithdrawals whether withdrawals to a bank need a verified INE
 */
public record AccountStanding(UUID userId, String status, boolean emailVerified, Instant lockedUntil,
        String identityStatus, Instant identitySubmittedAt, String identityRejectionReason,
        boolean identityRequiredForWithdrawals, Instant memberSince) {
}
