package com.irvinsegura.wallet.ledger;

import com.irvinsegura.wallet.shared.domain.DomainException;

public class UnsupportedCurrencyException extends DomainException {

    public UnsupportedCurrencyException(String currencyCode) {
        super("UNSUPPORTED_CURRENCY", "Currency %s is not supported".formatted(currencyCode));
    }
}
