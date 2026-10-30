package com.irvinsegura.wallet.shared.web;

import com.irvinsegura.wallet.shared.domain.Money;

/**
 * Amount as a decimal string, so JSON clients that parse numbers as floating point never lose cents.
 */
public record MoneyResponse(String amount, String currency) {

    public static MoneyResponse from(Money money) {
        return new MoneyResponse(money.toDecimal().toPlainString(), money.currency().getCurrencyCode());
    }
}
