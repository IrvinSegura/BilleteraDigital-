package com.irvinsegura.wallet.accounts;

import com.irvinsegura.wallet.shared.domain.DomainException;

public class WalletNotFoundException extends DomainException {

    public WalletNotFoundException() {
        super("WALLET_NOT_FOUND", "Wallet not found");
    }
}
