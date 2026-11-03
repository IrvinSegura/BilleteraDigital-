package com.irvinsegura.wallet.ledger;

import java.time.Instant;
import java.util.UUID;

/**
 * The platform's owners put money into the settlement account, e.g. to pay withdrawals before the payment provider
 * pays out the deposits behind them.
 *
 * @param reference the bank reference of the transfer that brought the money in
 */
public record CapitalContributed(UUID journalEntryId, long amountMinor, String currency, String reference,
        String reason, UUID contributedBy, Instant occurredAt) {
}
