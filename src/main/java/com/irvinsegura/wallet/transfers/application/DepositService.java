package com.irvinsegura.wallet.transfers.application;

import com.irvinsegura.wallet.accounts.WalletInfo;
import com.irvinsegura.wallet.accounts.Wallets;
import com.irvinsegura.wallet.ledger.Ledger;
import com.irvinsegura.wallet.ledger.NewJournalEntry;
import com.irvinsegura.wallet.ledger.RecordedEntry;
import com.irvinsegura.wallet.shared.domain.InvalidAmountException;
import com.irvinsegura.wallet.shared.domain.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Simulated top-up: money arrives at the platform's settlement account and is credited to the user's wallet.
 * A real integration would record it only after the payment provider confirms the charge.
 */
@Service
public class DepositService {

    static final String ENTRY_TYPE = "DEPOSIT";

    private final Wallets wallets;
    private final Ledger ledger;

    DepositService(Wallets wallets, Ledger ledger) {
        this.wallets = wallets;
        this.ledger = ledger;
    }

    @Transactional
    public DepositReceipt deposit(DepositCommand command) {
        WalletInfo wallet = wallets.getOwnedWallet(command.walletId(), command.userId());
        wallet.ensureActive();
        Money amount = Money.of(command.amount(), wallet.currency().getCurrencyCode());
        if (amount.isZero()) {
            throw new InvalidAmountException("Amount must be greater than zero");
        }

        RecordedEntry entry = ledger.record(new NewJournalEntry(ENTRY_TYPE, null,
                ledger.settlementAccount(wallet.currency()), wallet.ledgerAccountId(), amount));

        // Read inside the same transaction, while the wallet's ledger account is still locked
        return new DepositReceipt(entry.journalEntryId(), amount, ledger.balanceOf(wallet.ledgerAccountId()),
                entry.postedAt());
    }
}
