package com.irvinsegura.wallet.accounts.domain.model;

import com.irvinsegura.wallet.shared.domain.DomainException;
import java.util.Currency;

public class WalletAlreadyExistsException extends DomainException {

    public WalletAlreadyExistsException(Currency currency) {
        super("WALLET_ALREADY_EXISTS", "You already have a wallet in %s".formatted(currency.getCurrencyCode()));
    }
}
