package com.irvinsegura.wallet.transfers.application;

import com.irvinsegura.wallet.accounts.BankAccountInfo;
import com.irvinsegura.wallet.accounts.BankAccounts;
import com.irvinsegura.wallet.accounts.WalletInfo;
import com.irvinsegura.wallet.accounts.Wallets;
import com.irvinsegura.wallet.ledger.InsufficientFundsException;
import com.irvinsegura.wallet.ledger.Ledger;
import com.irvinsegura.wallet.ledger.NewJournalEntry;
import com.irvinsegura.wallet.ledger.RecordedEntry;
import com.irvinsegura.wallet.transfers.WithdrawalCompleted;
import com.irvinsegura.wallet.transfers.WithdrawalFailed;
import com.irvinsegura.wallet.transfers.domain.model.PayoutOrder;
import com.irvinsegura.wallet.transfers.domain.model.PayoutResult;
import com.irvinsegura.wallet.transfers.domain.model.Withdrawal;
import com.irvinsegura.wallet.transfers.domain.port.PayoutGateway;
import com.irvinsegura.wallet.transfers.domain.port.WithdrawalRepository;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sends payouts and applies what the provider answers, each answer under the withdrawal's row lock: the request
 * that made a withdrawal and the payout job may both get here, and the second one finds nothing left to do.
 * <p>
 * The provider is always called outside a transaction, so no lock is held while it takes its time.
 */
@Component
class WithdrawalSettlement {

    static final String PAID_ENTRY_TYPE = "WITHDRAWAL_PAID";
    static final String REVERSAL_ENTRY_TYPE = "WITHDRAWAL_REVERSAL";

    private static final Logger log = LoggerFactory.getLogger(WithdrawalSettlement.class);

    private final WithdrawalRepository withdrawalRepository;
    private final PayoutGateway payouts;
    private final Wallets wallets;
    private final BankAccounts bankAccounts;
    private final Ledger ledger;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final TransactionTemplate transaction;

    WithdrawalSettlement(WithdrawalRepository withdrawalRepository, PayoutGateway payouts, Wallets wallets,
            BankAccounts bankAccounts, Ledger ledger, ApplicationEventPublisher events, Clock clock,
            PlatformTransactionManager transactionManager) {
        this.withdrawalRepository = withdrawalRepository;
        this.payouts = payouts;
        this.wallets = wallets;
        this.bankAccounts = bankAccounts;
        this.ledger = ledger;
        this.events = events;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * Sends the payout if the provider has not accepted it yet, otherwise asks how it went.
     *
     * @throws com.irvinsegura.wallet.transfers.domain.model.PayoutProviderUnavailableException if the provider
     *         could not be reached; the withdrawal stays pending
     */
    WithdrawalReceipt advance(UUID withdrawalId) {
        Withdrawal withdrawal = withdrawalRepository.findById(withdrawalId).orElseThrow();
        if (!withdrawal.isPending()) {
            return WithdrawalReceipt.from(withdrawal);
        }
        PayoutResult result = withdrawal.providerReference() == null
                ? payouts.send(orderFor(withdrawal))
                : payouts.check(withdrawal.providerReference());
        try {
            return transaction.execute(status -> apply(withdrawalId, result));
        } catch (InsufficientFundsException e) {
            // The bank paid from the settlement account, which the ledger says cannot cover it: capital or the
            // provider's payouts must fund it first. Kept pending, so the payout job records it once they do.
            log.error("Settlement cannot cover the payout of withdrawal {}; fund it with capital", withdrawalId);
            return WithdrawalReceipt.from(withdrawalRepository.findById(withdrawalId).orElseThrow());
        }
    }

    // The bank account may have been removed since; the money still goes where the user chose when asking for it
    private PayoutOrder orderFor(Withdrawal withdrawal) {
        PayoutOrder.Destination destination = null;
        if (withdrawal.bankAccountId() != null) {
            BankAccountInfo bankAccount = bankAccounts.getBankAccount(withdrawal.bankAccountId());
            destination = new PayoutOrder.Destination(bankAccount.clabe(), bankAccount.holderName());
        }
        return new PayoutOrder(withdrawal.id(), withdrawal.amount(), destination);
    }

    private WithdrawalReceipt apply(UUID withdrawalId, PayoutResult result) {
        Withdrawal withdrawal = withdrawalRepository.findByIdForUpdate(withdrawalId).orElseThrow();
        if (!withdrawal.isPending()) {
            return WithdrawalReceipt.from(withdrawal);
        }
        switch (result.status()) {
            case PENDING -> {
                if (withdrawal.markSent(result.reference())) {
                    withdrawalRepository.save(withdrawal);
                }
            }
            case PAID -> pay(withdrawal, result);
            case FAILED -> giveBack(withdrawal, result);
        }
        return WithdrawalReceipt.from(withdrawal);
    }

    private void pay(Withdrawal withdrawal, PayoutResult result) {
        WalletInfo wallet = wallets.getOwnedWallet(withdrawal.walletId(), withdrawal.userId());
        // Both sides shrink: the platform no longer owes the money, and it has left the partner bank
        RecordedEntry entry = ledger.record(new NewJournalEntry(PAID_ENTRY_TYPE, withdrawal.id().toString(),
                ledger.payoutsInTransitAccount(wallet.currency()), ledger.settlementAccount(wallet.currency()),
                withdrawal.amount()));
        withdrawal.markPaid(result.reference(), entry.journalEntryId(), entry.postedAt());
        withdrawalRepository.save(withdrawal);
        events.publishEvent(new WithdrawalCompleted(withdrawal.id(), withdrawal.userId(), withdrawal.walletId(),
                withdrawal.amount().minorUnits(), withdrawal.amount().currency().getCurrencyCode(),
                entry.postedAt()));
    }

    // Credited back even if the wallet was frozen meanwhile: it is the user's own money coming home, not new money
    private void giveBack(Withdrawal withdrawal, PayoutResult result) {
        WalletInfo wallet = wallets.getOwnedWallet(withdrawal.walletId(), withdrawal.userId());
        // The money never left, so it no longer counts toward the limit of the day it was requested; done before
        // the ledger entry to lock the wallet before its ledger account, in the same order as a new withdrawal
        wallets.releaseOutgoing(withdrawal.walletId(), withdrawal.userId(), withdrawal.amount(),
                withdrawal.createdAt());
        RecordedEntry entry = ledger.record(new NewJournalEntry(REVERSAL_ENTRY_TYPE, withdrawal.id().toString(),
                ledger.payoutsInTransitAccount(wallet.currency()), wallet.ledgerAccountId(), withdrawal.amount()));
        withdrawal.fail(result.reference(), result.failureReason(), entry.journalEntryId(), clock.instant());
        withdrawalRepository.save(withdrawal);
        events.publishEvent(new WithdrawalFailed(withdrawal.id(), withdrawal.userId(), withdrawal.walletId(),
                withdrawal.amount().minorUnits(), withdrawal.amount().currency().getCurrencyCode(),
                result.failureReason(), entry.postedAt()));
        log.info("Payout of withdrawal {} failed ({}); the money went back to the wallet", withdrawal.id(),
                result.failureReason());
    }
}
