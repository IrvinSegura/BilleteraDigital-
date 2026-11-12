package com.irvinsegura.wallet.transfers.domain.model;

import com.irvinsegura.wallet.shared.domain.Money;
import java.util.Currency;
import java.util.Map;
import java.util.Objects;

/**
 * How much one administrator may adjust alone, per currency. Anything larger waits for a second administrator, and
 * currencies left out always do.
 */
public record AdjustmentPolicy(Map<Currency, Money> singleApprovalUpTo) {

    public AdjustmentPolicy {
        singleApprovalUpTo = Map.copyOf(Objects.requireNonNull(singleApprovalUpTo, "singleApprovalUpTo must not be null"));
    }

    public boolean needsSecondApproval(Money amount) {
        Money limit = singleApprovalUpTo.get(amount.currency());
        return limit == null || !limit.isGreaterThanOrEqualTo(amount);
    }
}
