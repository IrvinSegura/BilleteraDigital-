package com.irvinsegura.wallet.transfers.application;

import com.irvinsegura.wallet.accounts.WalletInfo;
import com.irvinsegura.wallet.accounts.Wallets;
import com.irvinsegura.wallet.identity.UserDirectory;
import com.irvinsegura.wallet.ledger.Ledger;
import com.irvinsegura.wallet.ledger.NewJournalEntry;
import com.irvinsegura.wallet.ledger.RecordedEntry;
import com.irvinsegura.wallet.shared.domain.InvalidAmountException;
import com.irvinsegura.wallet.shared.domain.Money;
import com.irvinsegura.wallet.transfers.DepositCompleted;
import com.irvinsegura.wallet.transfers.domain.model.Deposit;
import com.irvinsegura.wallet.transfers.domain.model.IdempotencyRecord;
import com.irvinsegura.wallet.transfers.domain.port.DepositRepository;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Simulated top-up: money arrives at the platform's settlement account and is credited to the user's wallet.
 * A real integration would record it only after the payment provider confirms the charge.
 */
@Service
public class DepositService {

    static final String ENTRY_TYPE = "DEPOSIT";

    private final Wallets wallets;
    private final UserDirectory users;
    private final Ledger ledger;
    private final DepositRepository depositRepository;
    private final IdempotentExecutor idempotentExecutor;
    private final ApplicationEventPublisher events;

    DepositService(Wallets wallets, UserDirectory users, Ledger ledger, DepositRepository depositRepository,
            IdempotentExecutor idempotentExecutor, ApplicationEventPublisher events) {
        this.wallets = wallets;
        this.users = users;
        this.ledger = ledger;
        this.depositRepository = depositRepository;
        this.idempotentExecutor = idempotentExecutor;
        this.events = events;
    }

    public Idempotent<DepositReceipt> deposit(DepositCommand command) {
        String requestHash = IdempotencyRecord.fingerprint(ENTRY_TYPE,
                command.walletId().toString(),
                command.amount().stripTrailingZeros().toPlainString());
        return idempotentExecutor.execute(command.userId(), command.idempotencyKey(), requestHash,
                depositId -> execute(depositId, command),
                depositId -> DepositReceipt.from(depositRepository.findById(depositId).orElseThrow()));
    }

    private DepositReceipt execute(UUID depositId, DepositCommand command) {
        // Unverified accounts may add money; only a disabled one is rejected
        users.authorizeDeposit(command.userId());
        // Locks the wallet so a freeze cannot commit while the money is coming in
        WalletInfo wallet = wallets.acceptIncoming(command.walletId(), command.userId());
        Money amount = Money.of(command.amount(), wallet.currency().getCurrencyCode());
        if (amount.isZero()) {
            throw new InvalidAmountException("Amount must be greater than zero");
        }

        RecordedEntry entry = ledger.record(new NewJournalEntry(ENTRY_TYPE, depositId.toString(),
                ledger.settlementAccount(wallet.currency()), wallet.ledgerAccountId(), amount));

        // Read inside the same transaction, while the wallet's ledger account is still locked
        Deposit deposit = new Deposit(depositId, command.userId(), wallet.id(), amount,
                ledger.balanceOf(wallet.ledgerAccountId()), entry.journalEntryId(), entry.postedAt());
        depositRepository.save(deposit);
        events.publishEvent(new DepositCompleted(depositId, command.userId(), wallet.id(), amount.minorUnits(),
                amount.currency().getCurrencyCode(), entry.postedAt()));
        return DepositReceipt.from(deposit);
    }
}
