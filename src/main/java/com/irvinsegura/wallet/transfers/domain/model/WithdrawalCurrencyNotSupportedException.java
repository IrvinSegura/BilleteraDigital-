package com.irvinsegura.wallet.transfers.domain.model;

import com.irvinsegura.wallet.shared.domain.DomainException;

/**
 * The payout provider can't pay withdrawals in the wallet's currency (STP pays only pesos), so none is made.
 */
public class WithdrawalCurrencyNotSupportedException extends DomainException {

    public WithdrawalCurrencyNotSupportedException(String currency) {
        super("WITHDRAWAL_CURRENCY_NOT_SUPPORTED", "Withdrawals in %s are not available".formatted(currency));
    }
}
