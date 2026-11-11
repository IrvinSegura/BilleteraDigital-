package com.irvinsegura.wallet.ledger.domain.model;

import com.irvinsegura.wallet.shared.domain.Money;

/**
 * All the customers' money in one currency: what the platform owes them.
 *
 * @param accounts how many customer accounts it is spread over
 */
public record CustomerBalance(Money balance, long accounts) {
}
