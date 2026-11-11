package com.irvinsegura.wallet.accounts.application;

import com.irvinsegura.wallet.identity.PersonalDataSource;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The bank accounts in use, as the owner sees them: never the full CLABE.
 */
@Component
class BankAccountPersonalData implements PersonalDataSource {

    private final BankAccountService bankAccounts;

    BankAccountPersonalData(BankAccountService bankAccounts) {
        this.bankAccounts = bankAccounts;
    }

    @Override
    public String section() {
        return "bankAccounts";
    }

    @Override
    public Object collect(UUID userId) {
        return bankAccounts.list(userId);
    }
}
