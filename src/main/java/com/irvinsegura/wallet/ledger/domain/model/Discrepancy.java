package com.irvinsegura.wallet.ledger.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * One failed reconciliation check. Amounts are minor units of {@code currency}.
 *
 * @param subjectId account or journal entry that failed; null for per-currency totals
 * @param expected  what the postings say, e.g. the net of an account's postings or an entry's debits
 * @param actual    what was found, e.g. the cached balance or an entry's credits
 */
public record Discrepancy(Kind kind, UUID subjectId, String currency, long expected, long actual) {

    public enum Kind {
        // Cached balance differs from the net of the account's postings
        ACCOUNT_BALANCE,
        // Debits differ from credits, fewer than two postings, or mixed currencies in one entry
        UNBALANCED_ENTRY,
        // Total asset balances differ from total liability balances in one currency
        TRIAL_BALANCE
    }

    public Discrepancy {
        Objects.requireNonNull(kind, "kind must not be null");
    }
}
