package com.irvinsegura.wallet.accounts;

import com.irvinsegura.wallet.shared.domain.DomainException;
import java.time.Instant;

/**
 * Part of the public API: a bank account added a moment ago cannot receive withdrawals yet, so someone who took over
 * an account cannot empty it into their own before the owner reads the notification.
 */
public class BankAccountCoolingOffException extends DomainException {

    private final Instant availableFrom;

    public BankAccountCoolingOffException(Instant availableFrom) {
        super("BANK_ACCOUNT_COOLING_OFF", "This bank account can receive withdrawals from " + availableFrom);
        this.availableFrom = availableFrom;
    }

    public Instant getAvailableFrom() {
        return availableFrom;
    }
}
