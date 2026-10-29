package com.irvinsegura.wallet.shared.domain;

import java.util.Currency;

public class CurrencyMismatchException extends DomainException {

    public CurrencyMismatchException(Currency expected, Currency actual) {
        super("CURRENCY_MISMATCH", "Expected currency %s but got %s".formatted(expected, actual));
    }
}
