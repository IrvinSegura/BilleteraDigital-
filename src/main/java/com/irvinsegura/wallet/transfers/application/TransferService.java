package com.irvinsegura.wallet.transfers.application;

import com.irvinsegura.wallet.accounts.WalletInfo;
import com.irvinsegura.wallet.accounts.Wallets;
import com.irvinsegura.wallet.identity.UserContact;
import com.irvinsegura.wallet.identity.UserDirectory;
import com.irvinsegura.wallet.identity.UserReference;
import com.irvinsegura.wallet.ledger.Ledger;
import com.irvinsegura.wallet.ledger.NewJournalEntry;
import com.irvinsegura.wallet.ledger.RecordedEntry;
import com.irvinsegura.wallet.shared.domain.InvalidAmountException;
import com.irvinsegura.wallet.shared.domain.Money;
import com.irvinsegura.wallet.transfers.TransferCompleted;
import com.irvinsegura.wallet.transfers.domain.model.IdempotencyRecord;
import com.irvinsegura.wallet.transfers.domain.model.RecipientNotFoundException;
import com.irvinsegura.wallet.transfers.domain.model.SelfTransferException;
import com.irvinsegura.wallet.transfers.domain.model.StepUpPolicy;
import com.irvinsegura.wallet.transfers.domain.model.Transfer;
import com.irvinsegura.wallet.transfers.domain.model.TransferNotFoundException;
import com.irvinsegura.wallet.transfers.domain.port.TransferRepository;
import com.irvinsegura.wallet.transfers.domain.port.TransferRepository.RecentRecipient;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transfers between users. The sender's wallet picks the currency; the recipient receives into their wallet in that
 * same currency.
 */
@Service
public class TransferService {

    static final String ENTRY_TYPE = "TRANSFER";

    private final Wallets wallets;
    private final UserDirectory users;
    private final Ledger ledger;
    private final TransferRepository transferRepository;
    private final IdempotentExecutor idempotentExecutor;
    private final StepUpPolicy stepUpPolicy;
    private final ApplicationEventPublisher events;

    TransferService(Wallets wallets, UserDirectory users, Ledger ledger, TransferRepository transferRepository,
            IdempotentExecutor idempotentExecutor, StepUpPolicy stepUpPolicy, ApplicationEventPublisher events) {
        this.wallets = wallets;
        this.users = users;
        this.ledger = ledger;
        this.transferRepository = transferRepository;
        this.idempotentExecutor = idempotentExecutor;
        this.stepUpPolicy = stepUpPolicy;
        this.events = events;
    }

    public Idempotent<TransferView> transfer(TransferCommand command) {
        String requestHash = IdempotencyRecord.fingerprint(ENTRY_TYPE,
                command.sourceWalletId().toString(),
                command.recipientEmail().strip().toLowerCase(Locale.ROOT),
                // 10, 10.0 and 10.00 are the same request
                command.amount().stripTrailingZeros().toPlainString(),
                Transfer.normalizeDescription(command.description()));
        Function<UUID, TransferView> loader =
                transferId -> view(transferRepository.findById(transferId).orElseThrow(), command.userId());
        // Retrying a transfer that went through needs no new proof (a used two-factor code wouldn't work twice)
        Optional<Idempotent<TransferView>> replay = idempotentExecutor.replayOf(command.userId(),
                command.idempotencyKey(), requestHash, loader);
        if (replay.isPresent()) {
            return replay.get();
        }
        confirmStepUpIfNeeded(command);
        return idempotentExecutor.execute(command.userId(), command.idempotencyKey(), requestHash,
                transferId -> execute(transferId, command), loader);
    }

    // Outside the transfer's transaction: the check commits on its own (so wrong attempts count) and locks the user
    // row. Inside, every request would hold two connections at once, and concurrent retries could drain the pool.
    private void confirmStepUpIfNeeded(TransferCommand command) {
        WalletInfo source = wallets.getOwnedWallet(command.sourceWalletId(), command.userId());
        Money amount = Money.of(command.amount(), source.currency().getCurrencyCode());
        Optional<UserReference> recipient = users.findByEmail(command.recipientEmail());
        // A transfer that can't happen fails inside with its own error; no proof is asked for it
        if (amount.isZero() || recipient.isEmpty() || recipient.get().id().equals(command.userId())) {
            return;
        }
        boolean paidBefore = transferRepository.existsBySenderAndRecipient(command.userId(), recipient.get().id());
        if (stepUpPolicy.transferNeedsStepUp(amount, paidBefore)) {
            users.confirmStepUp(command.userId(), command.twoFactorCode(), command.currentPassword());
        }
    }

    /**
     * A transfer the user sent or received.
     */
    @Transactional(readOnly = true)
    public TransferView getTransfer(UUID transferId, UUID userId) {
        return transferRepository.findById(transferId)
                .filter(transfer -> transfer.senderId().equals(userId) || transfer.recipientId().equals(userId))
                .map(transfer -> view(transfer, userId))
                .orElseThrow(TransferNotFoundException::new);
    }

    /**
     * The people the user has sent money to, the most recently paid first, to pick from instead of typing.
     */
    @Transactional(readOnly = true)
    public List<RecipientView> recentRecipients(UUID userId, int limit) {
        return transferRepository.recentRecipients(userId, limit).stream()
                .map(recent -> new RecipientView(recent.email(),
                        users.findContact(recent.recipientId()).map(UserContact::fullName)
                                .map(RecipientView::displayName).orElse(null),
                        recent.lastSentAt(), recent.timesSent()))
                .toList();
    }

    /**
     * Who would receive a transfer to {@code email} in {@code currency}, so the sender can check the name before
     * confirming.
     *
     * @throws RecipientNotFoundException if nobody with that email has a wallet in the currency, or it is the user
     */
    @Transactional(readOnly = true)
    public RecipientView findRecipient(UUID userId, String email, Currency currency) {
        UserReference recipient = users.findByEmail(email)
                .filter(found -> !found.id().equals(userId))
                .filter(found -> wallets.findWallet(found.id(), currency).isPresent())
                .orElseThrow(() -> new RecipientNotFoundException(currency));
        Optional<RecentRecipient> history = transferRepository.paymentsTo(userId, recipient.id());
        return new RecipientView(recipient.email(),
                users.findContact(recipient.id()).map(UserContact::fullName).map(RecipientView::displayName)
                        .orElse(null),
                history.map(RecentRecipient::lastSentAt).orElse(null),
                history.map(RecentRecipient::timesSent).orElse(0L));
    }

    private TransferView view(Transfer transfer, UUID viewerId) {
        return TransferView.of(transfer, viewerId, users.findContact(transfer.senderId()).orElse(null),
                users.findContact(transfer.recipientId()).orElse(null));
    }

    private TransferView execute(UUID transferId, TransferCommand command) {
        // Locks the sender's account until commit: a disable in progress is waited for and wins
        users.authorizeOutgoingMoney(command.userId());
        WalletInfo source = wallets.getOwnedWallet(command.sourceWalletId(), command.userId());
        Money amount = Money.of(command.amount(), source.currency().getCurrencyCode());
        if (amount.isZero()) {
            throw new InvalidAmountException("Amount must be greater than zero");
        }

        UserReference recipient = users.findByEmail(command.recipientEmail())
                .orElseThrow(() -> new RecipientNotFoundException(source.currency()));
        if (recipient.id().equals(command.userId())) {
            throw new SelfTransferException();
        }
        WalletInfo destination = wallets.findWallet(recipient.id(), source.currency())
                .orElseThrow(() -> new RecipientNotFoundException(source.currency()));
        // Last step before moving money, so the wallet rows stay locked as briefly as possible. Both are locked,
        // so neither can be frozen until the transfer commits.
        wallets.reserveTransfer(source.id(), command.userId(), destination.id(), amount);

        // The ledger locks both accounts in id order, so A->B and B->A at the same time cannot deadlock
        RecordedEntry entry = ledger.record(new NewJournalEntry(ENTRY_TYPE, transferId.toString(),
                source.ledgerAccountId(), destination.ledgerAccountId(), amount));

        Transfer transfer = Transfer.record(transferId, command.userId(), source.id(), recipient.id(),
                recipient.email(), destination.id(), amount, command.description(), entry.journalEntryId(),
                entry.postedAt());
        transferRepository.save(transfer);
        events.publishEvent(new TransferCompleted(transferId, command.userId(), source.id(), recipient.id(),
                destination.id(), amount.minorUnits(), amount.currency().getCurrencyCode(), entry.postedAt()));
        return view(transfer, command.userId());
    }
}
