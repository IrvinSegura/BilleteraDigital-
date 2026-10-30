package com.irvinsegura.wallet.accounts;

import com.irvinsegura.wallet.shared.domain.DomainException;

public class WalletNotActiveException extends DomainException {

    public WalletNotActiveException() {
        super("WALLET_NOT_ACTIVE", "Wallet is not active");
    }
}
