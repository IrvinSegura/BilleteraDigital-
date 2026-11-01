package com.irvinsegura.wallet.transfers.application;

import com.irvinsegura.wallet.accounts.WalletInfo;
import com.irvinsegura.wallet.accounts.Wallets;
import com.irvinsegura.wallet.identity.UserDirectory;
import com.irvinsegura.wallet.ledger.Ledger;
import com.irvinsegura.wallet.ledger.NewJournalEntry;
import com.irvinsegura.wallet.ledger.RecordedEntry;
import com.irvinsegura.wallet.shared.domain.InvalidAmountException;
import com.irvinsegura.wallet.shared.domain.Money;
import com.irvinsegura.wallet.transfers.WithdrawalCompleted;
import com.irvinsegura.wallet.transfers.domain.model.IdempotencyRecord;
import com.irvinsegura.wallet.transfers.domain.model.Withdrawal;
import com.irvinsegura.wallet.transfers.domain.port.WithdrawalRepository;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Simulated payout to the user's bank: the wallet is debited and the settlement account, which holds the money at
 * the partner bank, is credited. A real integration would send the payout order in the same step.
 */
@Service
public class WithdrawalService {

    static final String ENTRY_TYPE = "WITHDRAWAL";

    private final Wallets wallets;
    private final UserDirectory users;
    private final Ledger ledger;
    private final WithdrawalRepository withdrawalRepository;
    private final IdempotentExecutor idempotentExecutor;
    private final ApplicationEventPublisher events;

    WithdrawalService(Wallets wallets, UserDirectory users, Ledger ledger, WithdrawalRepository withdrawalRepository,
            IdempotentExecutor idempotentExecutor, ApplicationEventPublisher events) {
        this.wallets = wallets;
        this.users = users;
        this.ledger = ledger;
        this.withdrawalRepository = withdrawalRepository;
        this.idempotentExecutor = idempotentExecutor;
        this.events = events;
    }

    public Idempotent<WithdrawalReceipt> withdraw(WithdrawalCommand command) {
        String requestHash = IdempotencyRecord.fingerprint(ENTRY_TYPE,
                command.walletId().toString(),
                command.amount().stripTrailingZeros().toPlainString());
        return idempotentExecutor.execute(command.userId(), command.idempotencyKey(), requestHash,
                withdrawalId -> execute(withdrawalId, command),
                withdrawalId -> WithdrawalReceipt.from(withdrawalRepository.findById(withdrawalId).orElseThrow()));
    }

    private WithdrawalReceipt execute(UUID withdrawalId, WithdrawalCommand command) {
        // Locks the account until commit: a disable in progress is waited for and wins
        users.authorizeOutgoingMoney(command.userId());
        WalletInfo wallet = wallets.getOwnedWallet(command.walletId(), command.userId());
        Money amount = Money.of(command.amount(), wallet.currency().getCurrencyCode());
        if (amount.isZero()) {
            throw new InvalidAmountException("Amount must be greater than zero");
        }
        // Locks the wallet and checks it is active and within today's limit
        wallets.reserveOutgoing(wallet.id(), command.userId(), amount);

        RecordedEntry entry = ledger.record(new NewJournalEntry(ENTRY_TYPE, withdrawalId.toString(),
                wallet.ledgerAccountId(), ledger.settlementAccount(wallet.currency()), amount));

        Withdrawal withdrawal = new Withdrawal(withdrawalId, command.userId(), wallet.id(), amount,
                ledger.balanceOf(wallet.ledgerAccountId()), entry.journalEntryId(), entry.postedAt());
        withdrawalRepository.save(withdrawal);
        events.publishEvent(new WithdrawalCompleted(withdrawalId, command.userId(), wallet.id(), amount.minorUnits(),
                amount.currency().getCurrencyCode(), entry.postedAt()));
        return WithdrawalReceipt.from(withdrawal);
    }
}
