package com.irvinsegura.wallet.accounts.application;

import com.irvinsegura.wallet.ledger.DailyBalance;
import com.irvinsegura.wallet.shared.domain.Money;
import java.util.List;

/**
 * A wallet's closing balance per day, oldest first, and the balance it had before the first of them, so a chart
 * of a wallet opened today still draws a line from where it started.
 */
public record BalanceHistory(Money openingBalance, List<DailyBalance> days) {
}
