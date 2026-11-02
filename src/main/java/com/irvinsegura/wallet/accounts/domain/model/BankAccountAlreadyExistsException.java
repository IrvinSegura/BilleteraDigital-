package com.irvinsegura.wallet.accounts.domain.model;

import com.irvinsegura.wallet.shared.domain.DomainException;

public class BankAccountAlreadyExistsException extends DomainException {

    public BankAccountAlreadyExistsException() {
        super("BANK_ACCOUNT_ALREADY_EXISTS", "This CLABE is already one of your bank accounts");
    }
}
