package com.irvinsegura.wallet.transfers.domain.model;

import com.irvinsegura.wallet.shared.domain.Money;
import java.util.Currency;
import java.util.Map;
import java.util.Objects;

/**
 * When a transfer needs the owner to prove they are there (step-up): like strong customer authentication in PSD2,
 * every payment does, except small ones to someone the sender has paid before. Withdrawals always do, since the
 * money leaves the platform.
 *
 * @param exemptBelow the amount under which a transfer to a known recipient is exempt, by currency; a currency
 *                    without one has no exemption
 */
public record StepUpPolicy(Map<Currency, Money> exemptBelow) {

    public StepUpPolicy {
        exemptBelow = Map.copyOf(Objects.requireNonNull(exemptBelow, "exemptBelow must not be null"));
    }

    public boolean transferNeedsStepUp(Money amount, boolean recipientPaidBefore) {
        Money limit = exemptBelow.get(amount.currency());
        return !recipientPaidBefore || limit == null || amount.isGreaterThanOrEqualTo(limit);
    }
}
