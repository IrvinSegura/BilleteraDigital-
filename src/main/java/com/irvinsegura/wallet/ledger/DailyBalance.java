package com.irvinsegura.wallet.ledger;

import com.irvinsegura.wallet.shared.domain.Money;
import java.time.LocalDate;

/**
 * An account's balance at the end of {@code date}, a day in the zone the days were counted in.
 */
public record DailyBalance(LocalDate date, Money balance) {
}
