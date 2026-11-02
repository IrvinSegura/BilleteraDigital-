package com.irvinsegura.wallet.transfers.domain.model;

import com.irvinsegura.wallet.shared.domain.DomainException;

/**
 * The withdrawal names a destination its currency cannot be paid to, or none where one is needed.
 */
public class InvalidPayoutDestinationException extends DomainException {

    private InvalidPayoutDestinationException(String code, String message) {
        super(code, message);
    }

    public static InvalidPayoutDestinationException bankAccountRequired() {
        return new InvalidPayoutDestinationException("BANK_ACCOUNT_REQUIRED",
                "MXN withdrawals are paid by SPEI: send the bankAccountId of one of your bank accounts");
    }

    public static InvalidPayoutDestinationException bankAccountNotSupported(String currency) {
        return new InvalidPayoutDestinationException("BANK_ACCOUNT_NOT_SUPPORTED",
                "%s withdrawals cannot be paid to a CLABE; leave bankAccountId out".formatted(currency));
    }
}
