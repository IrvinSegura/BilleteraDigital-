package com.irvinsegura.wallet.transfers.domain.model;

import com.irvinsegura.wallet.shared.domain.Money;
import java.time.LocalDate;

/**
 * What finished in one day, of one kind and currency.
 *
 * @param kind {@code transfer}, {@code deposit} or {@code withdrawal}
 */
public record OperationDailyTotal(LocalDate date, String kind, long count, Money amount) {
}
