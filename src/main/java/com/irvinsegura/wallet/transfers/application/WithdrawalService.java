package com.irvinsegura.wallet.transfers.application;

import com.irvinsegura.wallet.accounts.BankAccounts;
import com.irvinsegura.wallet.accounts.WalletInfo;
import com.irvinsegura.wallet.accounts.Wallets;
import com.irvinsegura.wallet.identity.UserDirectory;
import com.irvinsegura.wallet.ledger.Ledger;
import com.irvinsegura.wallet.ledger.NewJournalEntry;
import com.irvinsegura.wallet.ledger.RecordedEntry;
import com.irvinsegura.wallet.shared.domain.InvalidAmountException;
import com.irvinsegura.wallet.shared.domain.Money;
import com.irvinsegura.wallet.transfers.WithdrawalRequested;
import com.irvinsegura.wallet.transfers.domain.model.IdempotencyRecord;
import com.irvinsegura.wallet.transfers.domain.model.InvalidPayoutDestinationException;
import com.irvinsegura.wallet.transfers.domain.model.PayoutProviderUnavailableException;
import com.irvinsegura.wallet.transfers.domain.model.Withdrawal;
import com.irvinsegura.wallet.transfers.domain.model.DepositPolicy;
import com.irvinsegura.wallet.transfers.domain.model.WithdrawalCurrencyNotSupportedException;
import com.irvinsegura.wallet.transfers.domain.model.WithdrawalNotFoundException;
import com.irvinsegura.wallet.transfers.domain.port.PayoutGateway;
import com.irvinsegura.wallet.transfers.domain.port.WithdrawalRepository;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Payout to the user's bank. The money leaves the wallet into transit when requested, the payout is sent once that
 * commits, and it ends paid or back in the wallet when the provider answers: right away with the simulated
 * provider, later with a real one.
 */
@Service
public class WithdrawalService {

    static final String ENTRY_TYPE = "WITHDRAWAL";
    // SPEI only moves pesos, and always into a CLABE
    private static final String SPEI_CURRENCY = "MXN";

    private static final Logger log = LoggerFactory.getLogger(WithdrawalService.class);

    private final Wallets wallets;
    private final BankAccounts bankAccounts;
    private final UserDirectory users;
    private final Ledger ledger;
    private final WithdrawalRepository withdrawalRepository;
    private final IdempotentExecutor idempotentExecutor;
    private final WithdrawalSettlement settlement;
    private final PayoutGateway payouts;
    private final DepositPolicy depositPolicy;
    private final ApplicationEventPublisher events;

    WithdrawalService(Wallets wallets, BankAccounts bankAccounts, UserDirectory users, Ledger ledger,
            WithdrawalRepository withdrawalRepository, IdempotentExecutor idempotentExecutor,
            WithdrawalSettlement settlement, PayoutGateway payouts, DepositPolicy depositPolicy,
            ApplicationEventPublisher events) {
        this.wallets = wallets;
        this.bankAccounts = bankAccounts;
        this.users = users;
        this.ledger = ledger;
        this.withdrawalRepository = withdrawalRepository;
        this.idempotentExecutor = idempotentExecutor;
        this.settlement = settlement;
        this.payouts = payouts;
        this.depositPolicy = depositPolicy;
        this.events = events;
    }

    /**
     * A replay returns the withdrawal as it is now, e.g. paid by the time the client retries.
     */
    public Idempotent<WithdrawalReceipt> withdraw(WithdrawalCommand command) {
        String requestHash = IdempotencyRecord.fingerprint(ENTRY_TYPE,
                command.walletId().toString(),
                command.amount().stripTrailingZeros().toPlainString(),
                String.valueOf(command.bankAccountId()));
        Function<UUID, WithdrawalReceipt> loader =
                withdrawalId -> WithdrawalReceipt.from(withdrawalRepository.findById(withdrawalId).orElseThrow());
        // Retrying a withdrawal that went through needs no new proof (a used two-factor code wouldn't work twice)
        Optional<Idempotent<WithdrawalReceipt>> replay = idempotentExecutor.replayOf(command.userId(),
                command.idempotencyKey(), requestHash, loader);
        if (replay.isPresent()) {
            return replay.get();
        }
        // Money leaving the platform always needs the owner's proof. Checked outside the withdrawal's transaction: it
        // commits on its own and locks the user row, and inside every request would hold two connections at once
        users.confirmStepUp(command.userId(), command.twoFactorCode(), command.currentPassword());
        Idempotent<WithdrawalReceipt> requested = idempotentExecutor.execute(command.userId(),
                command.idempotencyKey(), requestHash, withdrawalId -> request(withdrawalId, command), loader);
        if (requested.replayed()) {
            return requested;
        }
        return new Idempotent<>(sendPayout(requested.value()), false);
    }

    @Transactional(readOnly = true)
    public WithdrawalReceipt getWithdrawal(UUID userId, UUID walletId, UUID withdrawalId) {
        return withdrawalRepository.findById(withdrawalId)
                .filter(withdrawal -> withdrawal.userId().equals(userId) && withdrawal.walletId().equals(walletId))
                .map(WithdrawalReceipt::from)
                .orElseThrow(WithdrawalNotFoundException::new);
    }

    private WithdrawalReceipt request(UUID withdrawalId, WithdrawalCommand command) {
        // Locks the account until commit: a disable in progress is waited for and wins
        users.authorizeWithdrawal(command.userId());
        WalletInfo wallet = wallets.getOwnedWallet(command.walletId(), command.userId());
        Money amount = Money.of(command.amount(), wallet.currency().getCurrencyCode());
        if (amount.isZero()) {
            throw new InvalidAmountException("Amount must be greater than zero");
        }
        if (!payouts.pays(wallet.currency())) {
            throw new WithdrawalCurrencyNotSupportedException(wallet.currency().getCurrencyCode());
        }
        requireDestination(wallet, command);
        // Locks the wallet and checks it is active and within today's limit
        wallets.reserveOutgoing(wallet.id(), command.userId(), amount);

        // The money leaves the wallet now, so it cannot be spent twice while the payout is on its way
        RecordedEntry entry = ledger.record(new NewJournalEntry(ENTRY_TYPE, withdrawalId.toString(),
                wallet.ledgerAccountId(), ledger.payoutsInTransitAccount(wallet.currency()), amount));

        Withdrawal withdrawal = Withdrawal.request(withdrawalId, command.userId(), wallet.id(),
                command.bankAccountId(), amount, ledger.balanceOf(wallet.ledgerAccountId()), entry.journalEntryId(),
                entry.postedAt());
        withdrawalRepository.save(withdrawal);
        events.publishEvent(new WithdrawalRequested(withdrawalId, command.userId(), wallet.id(), amount.minorUnits(),
                wallet.currency().getCurrencyCode(), entry.postedAt()));
        return WithdrawalReceipt.from(withdrawal);
    }

    /**
     * The currencies withdrawals can be made in, so a wallet in any other offers none: those wallets hold money in,
     * and the payout provider pays only some of them.
     */
    public List<String> payableCurrencies() {
        // Every currency wallets are opened in has its deposit cap
        return depositPolicy.maxPerDeposit().keySet().stream()
                .filter(payouts::pays)
                .map(Currency::getCurrencyCode)
                .sorted()
                .toList();
    }

    private void requireDestination(WalletInfo wallet, WithdrawalCommand command) {
        String currency = wallet.currency().getCurrencyCode();
        if (!SPEI_CURRENCY.equals(currency)) {
            if (command.bankAccountId() != null) {
                throw InvalidPayoutDestinationException.bankAccountNotSupported(currency);
            }
            return;
        }
        if (command.bankAccountId() == null) {
            throw InvalidPayoutDestinationException.bankAccountRequired();
        }
        // Someone else's, or a removed one, answers 404 like a missing one
        bankAccounts.getActiveBankAccount(command.bankAccountId(), command.userId());
    }

    // After the commit: the provider is called over the network, never while holding locks
    private WithdrawalReceipt sendPayout(WithdrawalReceipt requested) {
        try {
            return settlement.advance(requested.id());
        } catch (PayoutProviderUnavailableException e) {
            // The money is safe in transit; the payout job sends it again
            log.warn("Could not send the payout of withdrawal {}; it will be retried", requested.id());
            return requested;
        }
    }
}
