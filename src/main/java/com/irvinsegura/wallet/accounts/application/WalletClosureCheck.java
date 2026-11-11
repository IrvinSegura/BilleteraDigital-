package com.irvinsegura.wallet.accounts.application;

import com.irvinsegura.wallet.identity.AccountClosureCheck;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Money left in any wallet must be withdrawn or sent first: a closed account can never move it again.
 */
@Component
class WalletClosureCheck implements AccountClosureCheck {

    static final String BALANCE = "BALANCE";

    private final WalletService wallets;

    WalletClosureCheck(WalletService wallets) {
        this.wallets = wallets;
    }

    @Override
    public Optional<String> blocker(UUID userId) {
        boolean moneyLeft = wallets.listWallets(userId).stream().anyMatch(wallet -> !wallet.balance().isZero());
        return moneyLeft ? Optional.of(BALANCE) : Optional.empty();
    }
}
