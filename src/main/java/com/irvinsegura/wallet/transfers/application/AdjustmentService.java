package com.irvinsegura.wallet.transfers.application;

import com.irvinsegura.wallet.accounts.WalletInfo;
import com.irvinsegura.wallet.accounts.Wallets;
import com.irvinsegura.wallet.identity.UserContact;
import com.irvinsegura.wallet.identity.UserDirectory;
import com.irvinsegura.wallet.ledger.Ledger;
import com.irvinsegura.wallet.ledger.NewJournalEntry;
import com.irvinsegura.wallet.ledger.RecordedEntry;
import com.irvinsegura.wallet.shared.domain.Money;
import com.irvinsegura.wallet.transfers.AdjustmentApplied;
import com.irvinsegura.wallet.transfers.AdjustmentRejected;
import com.irvinsegura.wallet.transfers.AdjustmentRequested;
import com.irvinsegura.wallet.transfers.domain.model.Adjustment;
import com.irvinsegura.wallet.transfers.domain.model.AdjustmentDirection;
import com.irvinsegura.wallet.transfers.domain.model.AdjustmentNotFoundException;
import com.irvinsegura.wallet.transfers.domain.model.AdjustmentPolicy;
import com.irvinsegura.wallet.transfers.domain.model.AdjustmentStatus;
import com.irvinsegura.wallet.transfers.domain.port.AdjustmentRepository;
import com.irvinsegura.wallet.transfers.domain.port.AdjustmentRepository.AdjustmentSlice;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adjustments to customers' wallets, the administrators' tool to settle a problem with money: a credit for a payment
 * that never arrived, a debit for money credited twice. Each one is a journal entry against the platform's own
 * adjustment accounts, with a reason the customer reads and an audit trail. Up to the policy's limit the requester
 * applies it; above it a second administrator has to, so no one person moves large sums. Administrators reach this
 * only with their second factor, enforced for all of {@code /api/v1/admin/**}.
 */
@Service
public class AdjustmentService {

    static final String CREDIT_ENTRY_TYPE = "ADJUSTMENT_CREDIT";
    static final String DEBIT_ENTRY_TYPE = "ADJUSTMENT_DEBIT";

    private final AdjustmentRepository adjustments;
    private final AdjustmentPolicy policy;
    private final Wallets wallets;
    private final Ledger ledger;
    private final UserDirectory users;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    AdjustmentService(AdjustmentRepository adjustments, AdjustmentPolicy policy, Wallets wallets, Ledger ledger,
            UserDirectory users, ApplicationEventPublisher events, Clock clock) {
        this.adjustments = adjustments;
        this.policy = policy;
        this.wallets = wallets;
        this.ledger = ledger;
        this.users = users;
        this.events = events;
        this.clock = clock;
    }

    /**
     * An adjustment with the people involved and whether it needs a second administrator.
     *
     * @param customerName  {@code null} if the account can't be read any more
     * @param secondApproval whether the policy asks a second administrator to apply it
     */
    public record AdjustmentView(Adjustment adjustment, String customerName, String customerEmail,
            String requestedByName, String decidedByName, boolean secondApproval) {
    }

    public record AdjustmentPage(List<AdjustmentView> items, int page, int size, boolean hasNext, long pending) {
    }

    /**
     * Records an adjustment, and applies it at once when it is within the requester's limit.
     *
     * @param ticketId the support ticket it settles, if any; kept as a reference only
     * @throws com.irvinsegura.wallet.accounts.WalletNotFoundException      if the wallet does not exist
     * @throws com.irvinsegura.wallet.ledger.InsufficientFundsException    if a debit is larger than the balance
     * @throws com.irvinsegura.wallet.shared.domain.InvalidAmountException if the amount is zero or has too many decimals
     */
    @Transactional
    public AdjustmentView request(UUID administratorId, UUID walletId, AdjustmentDirection direction, BigDecimal amount,
            String reason, UUID ticketId) {
        WalletInfo wallet = wallets.lockForAdjustment(walletId);
        Money money = Money.of(amount, wallet.currency().getCurrencyCode());
        Instant now = clock.instant();
        Adjustment adjustment = Adjustment.request(walletId, wallet.ownerId(), direction, money, reason, ticketId,
                administratorId, now);
        boolean secondApproval = policy.needsSecondApproval(money);
        if (secondApproval) {
            adjustments.insert(adjustment);
            events.publishEvent(new AdjustmentRequested(adjustment.id(), adjustment.userId(), walletId,
                    direction.name(), money.minorUnits(), money.currency().getCurrencyCode(), adjustment.reason(),
                    ticketId, administratorId, now));
        } else {
            // Inserted already applied: the row and the entry are committed together, or neither is
            post(adjustment, wallet, administratorId, false, now);
            adjustments.insert(adjustment);
            published(adjustment);
        }
        return view(adjustment);
    }

    /**
     * A second administrator applies an adjustment waiting for them.
     *
     * @throws AdjustmentNotFoundException if it does not exist
     * @throws com.irvinsegura.wallet.transfers.domain.model.AdjustmentNotPendingException    if it was already decided
     * @throws com.irvinsegura.wallet.transfers.domain.model.AdjustmentSelfApprovalException if its requester tries
     * @throws com.irvinsegura.wallet.ledger.InsufficientFundsException                     if a debit is larger than the
     *                                                                                        balance by now
     */
    @Transactional
    public AdjustmentView approve(UUID administratorId, UUID adjustmentId) {
        Adjustment adjustment = adjustments.findByIdForUpdate(adjustmentId)
                .orElseThrow(AdjustmentNotFoundException::new);
        adjustment.requirePending();
        WalletInfo wallet = wallets.lockForAdjustment(adjustment.walletId());
        post(adjustment, wallet, administratorId, true, clock.instant());
        adjustments.update(adjustment);
        published(adjustment);
        return view(adjustment);
    }

    /**
     * Turns down an adjustment waiting for approval; its requester may withdraw it this way too.
     *
     * @throws AdjustmentNotFoundException if it does not exist
     * @throws com.irvinsegura.wallet.transfers.domain.model.AdjustmentNotPendingException if it was already decided
     */
    @Transactional
    public AdjustmentView reject(UUID administratorId, UUID adjustmentId, String reason) {
        Adjustment adjustment = adjustments.findByIdForUpdate(adjustmentId)
                .orElseThrow(AdjustmentNotFoundException::new);
        Instant now = clock.instant();
        adjustment.reject(administratorId, reason, now);
        adjustments.update(adjustment);
        events.publishEvent(new AdjustmentRejected(adjustment.id(), adjustment.userId(), administratorId,
                adjustment.rejectionReason(), now));
        return view(adjustment);
    }

    @Transactional(readOnly = true)
    public AdjustmentPage page(AdjustmentStatus status, UUID ticketId, int page, int size) {
        AdjustmentSlice slice = adjustments.page(status, ticketId, page, size);
        return new AdjustmentPage(views(slice.items()), page, size, slice.hasNext(), adjustments.countPending());
    }

    @Transactional(readOnly = true)
    public AdjustmentView get(UUID adjustmentId) {
        return view(adjustments.findById(adjustmentId).orElseThrow(AdjustmentNotFoundException::new));
    }

    @Transactional(readOnly = true)
    public long countPending() {
        return adjustments.countPending();
    }

    // A credit is the platform's expense, a debit its revenue; the wallet's side moves the customer's balance
    private void post(Adjustment adjustment, WalletInfo wallet, UUID administratorId, boolean secondApproval,
            Instant now) {
        Money money = adjustment.amount();
        NewJournalEntry entry = adjustment.direction() == AdjustmentDirection.CREDIT
                ? new NewJournalEntry(CREDIT_ENTRY_TYPE, adjustment.id().toString(),
                        ledger.customerAdjustmentsAccount(money.currency()), wallet.ledgerAccountId(), money)
                : new NewJournalEntry(DEBIT_ENTRY_TYPE, adjustment.id().toString(), wallet.ledgerAccountId(),
                        ledger.adjustmentRecoveriesAccount(money.currency()), money);
        RecordedEntry recorded = ledger.record(entry);
        adjustment.apply(administratorId, secondApproval, recorded.journalEntryId(), now);
    }

    private void published(Adjustment adjustment) {
        Money money = adjustment.amount();
        events.publishEvent(new AdjustmentApplied(adjustment.id(), adjustment.userId(), adjustment.walletId(),
                adjustment.direction().name(), money.minorUnits(), money.currency().getCurrencyCode(),
                adjustment.reason(), adjustment.ticketId(), adjustment.requestedBy(), adjustment.decidedBy(),
                adjustment.decidedAt()));
    }

    private AdjustmentView view(Adjustment adjustment) {
        UserContact customer = users.findContact(adjustment.userId()).orElse(null);
        Map<UUID, String> names = users.findNames(people(List.of(adjustment)));
        return view(adjustment, customer == null ? null : customer.fullName(),
                customer == null ? null : customer.email(), names);
    }

    private List<AdjustmentView> views(List<Adjustment> found) {
        Set<UUID> people = people(found);
        found.forEach(adjustment -> people.add(adjustment.userId()));
        Map<UUID, String> names = users.findNames(people);
        return found.stream().map(adjustment -> view(adjustment, names.get(adjustment.userId()), null, names)).toList();
    }

    private AdjustmentView view(Adjustment adjustment, String customerName, String customerEmail,
            Map<UUID, String> names) {
        return new AdjustmentView(adjustment, customerName, customerEmail, names.get(adjustment.requestedBy()),
                adjustment.decidedBy() == null ? null : names.get(adjustment.decidedBy()),
                policy.needsSecondApproval(adjustment.amount()));
    }

    private static Set<UUID> people(List<Adjustment> found) {
        Set<UUID> people = new HashSet<>();
        found.forEach(adjustment -> {
            people.add(adjustment.requestedBy());
            if (adjustment.decidedBy() != null) {
                people.add(adjustment.decidedBy());
            }
        });
        return people;
    }
}
