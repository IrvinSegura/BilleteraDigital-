package com.irvinsegura.wallet.ledger;

import com.irvinsegura.wallet.shared.domain.Money;

/**
 * Money that raised ({@code moneyIn}) and lowered ({@code moneyOut}) an account's balance over a period.
 */
public record FlowTotals(Money moneyIn, Money moneyOut) {
}
