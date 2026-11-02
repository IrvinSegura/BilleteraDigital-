package com.irvinsegura.wallet.accounts.domain.model;

import com.irvinsegura.wallet.shared.domain.DomainException;

public class BankAccountLimitReachedException extends DomainException {

    public BankAccountLimitReachedException(int limit) {
        super("BANK_ACCOUNT_LIMIT_REACHED",
                "You can have at most %d bank accounts; remove one first".formatted(limit));
    }
}
