package com.irvinsegura.wallet.accounts.application;

import com.irvinsegura.wallet.shared.domain.Money;
import java.time.YearMonth;

/**
 * A wallet's month: the balance it started with, the money that came in and went out, and the balance it ended
 * with (so far, for the current month).
 */
public record MonthSummary(YearMonth month, Money openingBalance, Money moneyIn, Money moneyOut,
        Money closingBalance) {
}
