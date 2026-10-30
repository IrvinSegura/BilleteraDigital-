package com.irvinsegura.wallet.accounts;

import com.irvinsegura.wallet.shared.domain.DomainException;
import com.irvinsegura.wallet.shared.domain.Money;

public class DailyLimitExceededException extends DomainException {

    public DailyLimitExceededException(Money dailyLimit, Money remaining) {
        super("DAILY_LIMIT_EXCEEDED", "Daily limit of %s reached; you can still send %s today"
                .formatted(dailyLimit, remaining));
    }
}
