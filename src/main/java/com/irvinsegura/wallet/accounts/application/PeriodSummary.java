package com.irvinsegura.wallet.accounts.application;

import com.irvinsegura.wallet.shared.domain.Money;
import java.time.LocalDate;

/**
 * A wallet's period, from one calendar day to another, both included: the balance it started with, the money that
 * came in and went out, and the balance it ended with (so far, for a period that is not over).
 */
public record PeriodSummary(LocalDate from, LocalDate to, Money openingBalance, Money moneyIn, Money moneyOut,
        Money closingBalance) {
}
