package com.irvinsegura.wallet.accounts;

import com.irvinsegura.wallet.shared.domain.DomainException;

public class BankAccountNotFoundException extends DomainException {

    public BankAccountNotFoundException() {
        super("BANK_ACCOUNT_NOT_FOUND", "Bank account not found");
    }
}
