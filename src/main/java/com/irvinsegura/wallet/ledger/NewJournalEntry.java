package com.irvinsegura.wallet.ledger;

import com.irvinsegura.wallet.shared.domain.Money;
import java.util.Objects;
import java.util.UUID;

/**
 * Moves {@code amount} by debiting one account and crediting another. Whether a side increases or decreases a
 * balance depends on the account type: a deposit debits the settlement asset and credits the user's liability,
 * so both grow.
 *
 * @param type      short uppercase label shown in statements, e.g. {@code DEPOSIT}
 * @param reference optional id of the business operation that caused the entry
 */
public record NewJournalEntry(String type, String reference, UUID debitAccountId, UUID creditAccountId,
        Money amount) {

    public NewJournalEntry {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(debitAccountId, "debitAccountId must not be null");
        Objects.requireNonNull(creditAccountId, "creditAccountId must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
    }
}
