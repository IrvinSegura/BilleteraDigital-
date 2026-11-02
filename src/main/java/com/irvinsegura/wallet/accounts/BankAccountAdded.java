package com.irvinsegura.wallet.accounts;

import java.time.Instant;
import java.util.UUID;

/**
 * Only the last four digits travel in events, which are stored in the outbox and copied into the audit log.
 */
public record BankAccountAdded(UUID bankAccountId, UUID ownerId, String bankCode, String clabeLastFour,
        Instant occurredAt) {
}
