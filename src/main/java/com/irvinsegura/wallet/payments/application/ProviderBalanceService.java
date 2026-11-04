package com.irvinsegura.wallet.payments.application;

import com.irvinsegura.wallet.ledger.InsufficientFundsException;
import com.irvinsegura.wallet.ledger.Ledger;
import com.irvinsegura.wallet.ledger.NewJournalEntry;
import com.irvinsegura.wallet.ledger.UnsupportedCurrencyException;
import com.irvinsegura.wallet.payments.PaymentProviderUnavailableException;
import com.irvinsegura.wallet.payments.ProviderTransactionResolved;
import com.irvinsegura.wallet.payments.domain.model.BalanceTransaction;
import com.irvinsegura.wallet.payments.domain.model.Payment;
import com.irvinsegura.wallet.payments.domain.model.PaymentStatus;
import com.irvinsegura.wallet.payments.domain.model.ProviderBalance;
import com.irvinsegura.wallet.payments.domain.model.ProviderBalanceSnapshot;
import com.irvinsegura.wallet.payments.domain.model.ProviderTransaction;
import com.irvinsegura.wallet.payments.domain.model.ProviderTransactionNotFoundException;
import com.irvinsegura.wallet.payments.domain.model.ProviderTransactionNotUnmatchedException;
import com.irvinsegura.wallet.payments.domain.port.PaymentGateway;
import com.irvinsegura.wallet.payments.domain.port.PaymentRepository;
import com.irvinsegura.wallet.payments.domain.port.ProviderBalanceSnapshotRepository;
import com.irvinsegura.wallet.payments.domain.port.ProviderTransactionRepository;
import com.irvinsegura.wallet.shared.domain.Money;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Follows the money a provider holds (Stripe), so the clearing account always says what is really there. Every
 * movement of the provider's balance is read once and recorded: fees as an expense, payouts as money reaching the
 * partner bank. Charges and refunds were already recorded when the payment succeeded or was refunded, so they only
 * need to be recognised. Whatever cannot be recognised is left unmatched for a person.
 * <p>
 * Instances running it at the same time is harmless: rows are inserted once and each is recorded under its row
 * lock, so the second instance to get there finds nothing left to do.
 */
@Service
public class ProviderBalanceService {

    static final String FEE_ENTRY_TYPE = "PROVIDER_FEE";
    static final String FEE_REFUND_ENTRY_TYPE = "PROVIDER_FEE_REFUND";
    static final String PAYOUT_ENTRY_TYPE = "PROVIDER_PAYOUT";
    static final String PAYOUT_REVERSAL_ENTRY_TYPE = "PROVIDER_PAYOUT_REVERSAL";

    private static final Logger log = LoggerFactory.getLogger(ProviderBalanceService.class);
    private static final int BATCH_SIZE = 500;

    private final PaymentGateway gateway;
    private final ProviderTransactionRepository transactions;
    private final ProviderBalanceSnapshotRepository snapshots;
    private final PaymentRepository payments;
    private final Ledger ledger;
    private final Clock clock;
    private final TransactionTemplate transaction;
    private final ApplicationEventPublisher events;
    private final Duration overlap;
    private final Duration matchWindow;

    ProviderBalanceService(PaymentGateway gateway, ProviderTransactionRepository transactions,
            ProviderBalanceSnapshotRepository snapshots, PaymentRepository payments, Ledger ledger, Clock clock,
            PlatformTransactionManager transactionManager, ApplicationEventPublisher events,
            @Value("${wallet.payments.provider-sync.overlap}") Duration overlap,
            @Value("${wallet.payments.provider-sync.match-window}") Duration matchWindow) {
        this.gateway = gateway;
        this.transactions = transactions;
        this.snapshots = snapshots;
        this.payments = payments;
        this.ledger = ledger;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
        this.events = events;
        this.overlap = overlap;
        this.matchWindow = matchWindow;
    }

    /**
     * Reads what is new at the provider, records what can be recorded and compares balances. Does nothing for a
     * provider that does not hold money.
     *
     * @throws PaymentProviderUnavailableException if the provider could not be reached; nothing is lost, the next
     *         run reads the same window again
     */
    public SyncResult synchronize() {
        if (!gateway.holdsFunds()) {
            return new SyncResult(0, 0, 0);
        }
        String provider = gateway.name();
        int read = 0;
        Optional<Instant> since = transactions.findLatestCreatedAt(provider)
                // Read again a little before the newest one: the provider may list late arrivals with earlier times
                .map(latest -> latest.minus(overlap))
                .or(() -> payments.findEarliestCreatedAt(provider));
        if (since.isPresent()) {
            Instant now = clock.instant();
            for (BalanceTransaction found : gateway.balanceTransactionsSince(since.get())) {
                if (transactions.recordIfNew(provider, found, now)) {
                    read++;
                }
            }
        }
        int posted = 0;
        int unmatched = 0;
        for (String id : transactions.findPendingIds(provider, BATCH_SIZE)) {
            try {
                ProviderTransaction.Status status = transaction.execute(tx -> settle(provider, id));
                if (status == ProviderTransaction.Status.POSTED) {
                    posted++;
                } else if (status == ProviderTransaction.Status.UNMATCHED) {
                    unmatched++;
                }
            } catch (InsufficientFundsException e) {
                // E.g. a fee on a refunded payment before any other money arrived; stays pending until it fits
                log.warn("Provider transaction {} does not fit the ledger yet; trying again on the next run", id);
            }
        }
        compareBalances();
        return new SyncResult(read, posted, unmatched);
    }

    /**
     * Provider balance minus the clearing account, per currency, as of the last run. Zero once every movement is
     * recorded; it stays off while a payment's webhook has not arrived yet.
     */
    public Map<String, Long> balanceDifferences() {
        return balanceComparisons().stream().collect(Collectors.toMap(ProviderBalanceSnapshot::currency,
                ProviderBalanceSnapshot::differenceMinor));
    }

    /**
     * The last comparison per currency for the provider in use; empty until the first one, or with a provider that
     * does not hold money.
     */
    public List<ProviderBalanceSnapshot> balanceComparisons() {
        return snapshots.findByProvider(gateway.name());
    }

    public String providerName() {
        return gateway.name();
    }

    public boolean providerHoldsFunds() {
        return gateway.holdsFunds();
    }

    /**
     * For a person to look at, newest first.
     *
     * @param status null for every status
     */
    @Transactional(readOnly = true)
    public List<ProviderTransaction> transactions(ProviderTransaction.Status status, int limit) {
        return transactions.find(status, limit);
    }

    /**
     * Closes an unmatched movement with what the person who looked at it concluded. Posts nothing: if the ledger needs
     * correcting, that is a separate step.
     *
     * @throws ProviderTransactionNotFoundException     if there is no such movement
     * @throws ProviderTransactionNotUnmatchedException if it is not waiting for a person, e.g. it was already closed
     */
    @Transactional
    public ProviderTransaction resolve(String provider, String id, UUID administratorId, String reason) {
        ProviderTransaction row = transactions.findByIdForUpdate(provider, id)
                .orElseThrow(ProviderTransactionNotFoundException::new);
        if (row.status() != ProviderTransaction.Status.UNMATCHED) {
            throw new ProviderTransactionNotUnmatchedException();
        }
        Instant now = clock.instant();
        transactions.resolve(provider, id, administratorId, reason, now);
        events.publishEvent(new ProviderTransactionResolved(provider, id, administratorId, reason, now));
        return transactions.findByIdForUpdate(provider, id).orElseThrow();
    }

    private ProviderTransaction.Status settle(String provider, String id) {
        ProviderTransaction row = transactions.findByIdForUpdate(provider, id).orElseThrow();
        if (row.status() != ProviderTransaction.Status.PENDING) {
            // Another instance settled it meanwhile, and counted it
            return null;
        }
        BalanceTransaction found = row.transaction();
        Outcome outcome;
        try {
            outcome = switch (found.type()) {
                case CHARGE -> recordCharge(found);
                case REFUND -> recognizeRefund(found);
                case PAYOUT -> recordPayout(found);
                case PAYOUT_REVERSAL -> recordPayoutReversal(found);
                case FEE -> recordFee(found);
                case OTHER -> Outcome.unmatched("Not handled: " + found.providerType());
            };
        } catch (UnsupportedCurrencyException e) {
            outcome = Outcome.unmatched("Currency " + found.currency() + " is not supported");
        }
        if (outcome.status() == ProviderTransaction.Status.PENDING) {
            return outcome.status();
        }
        transactions.settle(provider, id, outcome.status(), outcome.note(), clock.instant());
        if (outcome.status() == ProviderTransaction.Status.UNMATCHED) {
            log.warn("Provider transaction {} needs a person: {}", id, outcome.note());
        }
        return outcome.status();
    }

    private Outcome recordCharge(BalanceTransaction charge) {
        Optional<Payment> payment = payment(charge);
        if (payment.isEmpty() || !payment.get().isCollected()) {
            // Its webhook may still be on its way
            return isRecent(charge) ? Outcome.WAIT : Outcome.unmatched("No collected payment for this charge");
        }
        if (payment.get().receivedEntryId() == null) {
            return Outcome.unmatched("Payment collected before the clearing account existed");
        }
        Currency currency = currency(charge);
        if (charge.feeMinor() > 0) {
            ledger.record(new NewJournalEntry(FEE_ENTRY_TYPE, charge.id(), ledger.providerFeesAccount(currency),
                    ledger.providerClearingAccount(currency), Money.ofMinor(charge.feeMinor(), charge.currency())));
        }
        return Outcome.POSTED;
    }

    // Refunds the application asked for are recorded when the provider confirms them; any other refund was made
    // outside the application, e.g. from the provider's dashboard
    private Outcome recognizeRefund(BalanceTransaction refund) {
        Optional<Payment> payment = payment(refund);
        if (payment.isPresent() && payment.get().status() == PaymentStatus.REFUND_PENDING) {
            return Outcome.WAIT;
        }
        if (payment.isPresent() && payment.get().status() == PaymentStatus.REFUNDED
                && payment.get().receivedEntryId() != null) {
            return Outcome.POSTED;
        }
        return Outcome.unmatched("Refund the application did not ask for");
    }

    private Outcome recordPayout(BalanceTransaction payout) {
        if (payout.amountMinor() >= 0) {
            return Outcome.unmatched("Payout that did not take money from the provider");
        }
        Currency currency = currency(payout);
        ledger.record(new NewJournalEntry(PAYOUT_ENTRY_TYPE, payout.id(), ledger.settlementAccount(currency),
                ledger.providerClearingAccount(currency), Money.ofMinor(-payout.amountMinor(), payout.currency())));
        if (payout.feeMinor() > 0) {
            ledger.record(new NewJournalEntry(FEE_ENTRY_TYPE, payout.id(), ledger.providerFeesAccount(currency),
                    ledger.providerClearingAccount(currency), Money.ofMinor(payout.feeMinor(), payout.currency())));
        }
        return Outcome.POSTED;
    }

    private Outcome recordPayoutReversal(BalanceTransaction reversal) {
        if (reversal.amountMinor() <= 0) {
            return Outcome.unmatched("Payout reversal that did not return money to the provider");
        }
        Currency currency = currency(reversal);
        ledger.record(new NewJournalEntry(PAYOUT_REVERSAL_ENTRY_TYPE, reversal.id(),
                ledger.providerClearingAccount(currency), ledger.settlementAccount(currency),
                Money.ofMinor(reversal.amountMinor(), reversal.currency())));
        return Outcome.POSTED;
    }

    // A negative amount is a fee taken; a positive one gives part of an earlier fee back
    private Outcome recordFee(BalanceTransaction fee) {
        if (fee.amountMinor() == 0) {
            return Outcome.POSTED;
        }
        Currency currency = currency(fee);
        Money amount = Money.ofMinor(Math.abs(fee.amountMinor()), fee.currency());
        ledger.record(fee.amountMinor() < 0
                ? new NewJournalEntry(FEE_ENTRY_TYPE, fee.id(), ledger.providerFeesAccount(currency),
                        ledger.providerClearingAccount(currency), amount)
                : new NewJournalEntry(FEE_REFUND_ENTRY_TYPE, fee.id(), ledger.providerClearingAccount(currency),
                        ledger.providerFeesAccount(currency), amount));
        return Outcome.POSTED;
    }

    private Optional<Payment> payment(BalanceTransaction found) {
        return found.providerPaymentId() == null
                ? Optional.empty()
                : payments.findByProviderPaymentId(gateway.name(), found.providerPaymentId());
    }

    private boolean isRecent(BalanceTransaction found) {
        return found.createdAt().isAfter(clock.instant().minus(matchWindow));
    }

    private void compareBalances() {
        for (ProviderBalance balance : gateway.balance()) {
            try {
                Currency currency = currency(balance.currency());
                UUID clearing = ledger.providerClearingAccount(currency);
                long recorded = ledger.balanceOf(clearing).minorUnits();
                ProviderBalanceSnapshot snapshot = new ProviderBalanceSnapshot(gateway.name(),
                        currency.getCurrencyCode(), balance.amountMinor(), recorded, clock.instant());
                snapshots.save(snapshot);
                if (snapshot.differenceMinor() != 0) {
                    log.info("Provider holds {} {} more than the clearing account records",
                            snapshot.differenceMinor(), currency.getCurrencyCode());
                }
            } catch (UnsupportedCurrencyException e) {
                log.warn("Provider holds {} in unsupported currency {}", balance.amountMinor(), balance.currency());
            }
        }
    }

    private static Currency currency(BalanceTransaction found) {
        return currency(found.currency());
    }

    private static Currency currency(String code) {
        try {
            return Currency.getInstance(code);
        } catch (IllegalArgumentException e) {
            throw new UnsupportedCurrencyException(code);
        }
    }

    // What became of a pending transaction; PENDING means it waits for the next run
    private record Outcome(ProviderTransaction.Status status, String note) {

        static final Outcome POSTED = new Outcome(ProviderTransaction.Status.POSTED, null);
        static final Outcome WAIT = new Outcome(ProviderTransaction.Status.PENDING, null);

        static Outcome unmatched(String note) {
            return new Outcome(ProviderTransaction.Status.UNMATCHED, note);
        }
    }

    /**
     * @param read      transactions read from the provider for the first time
     * @param posted    transactions recorded (or recognised) in the ledger
     * @param unmatched transactions left for a person
     */
    public record SyncResult(int read, int posted, int unmatched) {
    }
}
