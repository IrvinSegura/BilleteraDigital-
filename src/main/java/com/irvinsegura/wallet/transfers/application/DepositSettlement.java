package com.irvinsegura.wallet.transfers.application;

import com.irvinsegura.wallet.accounts.WalletInfo;
import com.irvinsegura.wallet.accounts.WalletNotActiveException;
import com.irvinsegura.wallet.accounts.Wallets;
import com.irvinsegura.wallet.identity.AccountDisabledException;
import com.irvinsegura.wallet.identity.UserDirectory;
import com.irvinsegura.wallet.ledger.Ledger;
import com.irvinsegura.wallet.ledger.NewJournalEntry;
import com.irvinsegura.wallet.ledger.RecordedEntry;
import com.irvinsegura.wallet.payments.Payments;
import com.irvinsegura.wallet.shared.domain.DomainException;
import com.irvinsegura.wallet.transfers.DepositCompleted;
import com.irvinsegura.wallet.transfers.DepositRejected;
import com.irvinsegura.wallet.transfers.domain.model.Deposit;
import com.irvinsegura.wallet.transfers.domain.model.DepositStatus;
import com.irvinsegura.wallet.transfers.domain.port.DepositRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Moves a deposit out of the pending state, each change under the deposit's row lock: the request that made a
 * deposit and the listener of its payment's outcome may both get here, and the second one finds nothing to do.
 * <p>
 * Each step runs in a transaction of its own, even inside an event listener's: when crediting fails because the
 * wallet refuses the money, that transaction is rolled back whole and the rejection is recorded in a fresh one.
 */
@Component
class DepositSettlement {

    private static final Logger log = LoggerFactory.getLogger(DepositSettlement.class);

    private final Wallets wallets;
    private final UserDirectory users;
    private final Ledger ledger;
    private final DepositRepository depositRepository;
    private final Payments payments;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final TransactionTemplate transaction;

    DepositSettlement(Wallets wallets, UserDirectory users, Ledger ledger, DepositRepository depositRepository,
            Payments payments, ApplicationEventPublisher events, Clock clock,
            PlatformTransactionManager transactionManager) {
        this.wallets = wallets;
        this.users = users;
        this.ledger = ledger;
        this.depositRepository = depositRepository;
        this.payments = payments;
        this.events = events;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Credits a deposit whose payment succeeded, or rejects it (and refunds the payment) if the wallet or the account
     * no longer accepts money. Returns empty if no deposit has this id.
     */
    Optional<DepositReceipt> complete(UUID depositId) {
        try {
            return transaction.execute(status -> credit(depositId));
        } catch (WalletNotActiveException | AccountDisabledException e) {
            return transaction.execute(status -> reject(depositId, e));
        }
    }

    DepositReceipt awaitPayment(UUID depositId, String checkoutUrl, Instant expiresAt) {
        return transaction.execute(status -> {
            Deposit deposit = depositRepository.findByIdForUpdate(depositId).orElseThrow();
            // The payment may already have been settled by a quick webhook
            if (deposit.isPending()) {
                deposit.awaitPayment(checkoutUrl, expiresAt);
                depositRepository.save(deposit);
            }
            return DepositReceipt.from(deposit);
        });
    }

    /**
     * Ends a deposit whose payment will never succeed. Does nothing if it is no longer pending.
     */
    void abandon(UUID depositId, DepositStatus finalStatus) {
        transaction.executeWithoutResult(status -> depositRepository.findByIdForUpdate(depositId).ifPresent(deposit -> {
            if (deposit.abandon(finalStatus, clock.instant())) {
                depositRepository.save(deposit);
            }
        }));
    }

    private Optional<DepositReceipt> credit(UUID depositId) {
        Optional<Deposit> found = depositRepository.findByIdForUpdate(depositId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Deposit deposit = found.get();
        if (!deposit.isPending()) {
            if (deposit.status() != DepositStatus.COMPLETED) {
                // Paid after it expired or failed here, which a provider can still report; the money goes back
                log.warn("Deposit {} is {} but its payment succeeded; refunding it", depositId, deposit.status());
                payments.refund(depositId);
            }
            return Optional.of(DepositReceipt.from(deposit));
        }
        // Checked again now that the money is here: the account or wallet may have changed since the request
        users.authorizeDeposit(deposit.userId());
        // Locks the wallet so a freeze cannot commit while the money is coming in
        WalletInfo wallet = wallets.acceptIncoming(deposit.walletId(), deposit.userId());
        // The payments module recorded the collected money as unapplied; the deposit applies it to the wallet
        RecordedEntry entry = ledger.record(new NewJournalEntry(DepositService.ENTRY_TYPE, depositId.toString(),
                ledger.unappliedPaymentsAccount(wallet.currency()), wallet.ledgerAccountId(), deposit.amount()));
        // Read inside the same transaction, while the wallet's ledger account is still locked
        deposit.complete(ledger.balanceOf(wallet.ledgerAccountId()), entry.journalEntryId(), entry.postedAt());
        depositRepository.save(deposit);
        events.publishEvent(new DepositCompleted(depositId, deposit.userId(), deposit.walletId(),
                deposit.amount().minorUnits(), deposit.amount().currency().getCurrencyCode(), entry.postedAt()));
        return Optional.of(DepositReceipt.from(deposit));
    }

    private Optional<DepositReceipt> reject(UUID depositId, DomainException reason) {
        Deposit deposit = depositRepository.findByIdForUpdate(depositId).orElseThrow();
        if (!deposit.isPending()) {
            return Optional.of(DepositReceipt.from(deposit));
        }
        Instant now = clock.instant();
        deposit.reject(reason.getCode(), now);
        depositRepository.save(deposit);
        payments.refund(depositId);
        events.publishEvent(new DepositRejected(depositId, deposit.userId(), deposit.walletId(),
                deposit.amount().minorUnits(), deposit.amount().currency().getCurrencyCode(), reason.getCode(), now));
        log.info("Deposit {} rejected ({}); its payment is being refunded", depositId, reason.getCode());
        return Optional.of(DepositReceipt.from(deposit));
    }
}
