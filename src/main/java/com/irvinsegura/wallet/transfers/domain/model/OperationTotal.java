package com.irvinsegura.wallet.transfers.domain.model;

import com.irvinsegura.wallet.shared.domain.Money;

/**
 * How many operations of a kind finished in a period, and how much money they moved, in one currency.
 *
 * @param kind {@code transfer}, {@code deposit} or {@code withdrawal}
 */
public record OperationTotal(String kind, long count, Money amount) {
}
