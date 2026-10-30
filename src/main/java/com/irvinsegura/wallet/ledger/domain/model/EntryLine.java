package com.irvinsegura.wallet.ledger.domain.model;

import com.irvinsegura.wallet.shared.domain.Money;
import java.util.Objects;
import java.util.UUID;

/**
 * One requested side of a journal entry, before it is applied to the account.
 */
public record EntryLine(UUID accountId, Side side, Money amount) {

    public EntryLine {
        Objects.requireNonNull(accountId, "accountId must not be null");
        Objects.requireNonNull(side, "side must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
    }
}
