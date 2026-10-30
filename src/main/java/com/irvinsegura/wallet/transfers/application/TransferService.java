package com.irvinsegura.wallet.transfers.application;

import com.irvinsegura.wallet.accounts.WalletInfo;
import com.irvinsegura.wallet.accounts.Wallets;
import com.irvinsegura.wallet.identity.UserDirectory;
import com.irvinsegura.wallet.identity.UserReference;
import com.irvinsegura.wallet.ledger.Ledger;
import com.irvinsegura.wallet.ledger.NewJournalEntry;
import com.irvinsegura.wallet.ledger.RecordedEntry;
import com.irvinsegura.wallet.shared.domain.InvalidAmountException;
import com.irvinsegura.wallet.shared.domain.Money;
import com.irvinsegura.wallet.transfers.domain.model.IdempotencyRecord;
import com.irvinsegura.wallet.transfers.domain.model.RecipientNotFoundException;
import com.irvinsegura.wallet.transfers.domain.model.SelfTransferException;
import com.irvinsegura.wallet.transfers.domain.model.Transfer;
import com.irvinsegura.wallet.transfers.domain.model.TransferNotFoundException;
import com.irvinsegura.wallet.transfers.domain.port.TransferRepository;
import java.util.Locale;
import java.util.UUID;
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

    TransferService(Wallets wallets, UserDirectory users, Ledger ledger, TransferRepository transferRepository,
            IdempotentExecutor idempotentExecutor) {
        this.wallets = wallets;
        this.users = users;
        this.ledger = ledger;
        this.transferRepository = transferRepository;
        this.idempotentExecutor = idempotentExecutor;
    }

    public Idempotent<TransferView> transfer(TransferCommand command) {
        String requestHash = IdempotencyRecord.fingerprint(ENTRY_TYPE,
                command.sourceWalletId().toString(),
                command.recipientEmail().strip().toLowerCase(Locale.ROOT),
                // 10, 10.0 and 10.00 are the same request
                command.amount().stripTrailingZeros().toPlainString(),
                Transfer.normalizeDescription(command.description()));
        return idempotentExecutor.execute(command.userId(), command.idempotencyKey(), requestHash,
                transferId -> execute(transferId, command),
                transferId -> TransferView.from(transferRepository.findById(transferId).orElseThrow()));
    }

    @Transactional(readOnly = true)
    public TransferView getTransfer(UUID transferId, UUID userId) {
        return transferRepository.findById(transferId)
                .filter(transfer -> transfer.senderId().equals(userId))
                .map(TransferView::from)
                .orElseThrow(TransferNotFoundException::new);
    }

    private TransferView execute(UUID transferId, TransferCommand command) {
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
        destination.ensureActive();
        // Last step before moving money, so the sender's wallet row stays locked as briefly as possible
        wallets.reserveOutgoing(source.id(), command.userId(), amount);

        // The ledger locks both accounts in id order, so A->B and B->A at the same time cannot deadlock
        RecordedEntry entry = ledger.record(new NewJournalEntry(ENTRY_TYPE, transferId.toString(),
                source.ledgerAccountId(), destination.ledgerAccountId(), amount));

        Transfer transfer = Transfer.record(transferId, command.userId(), source.id(), recipient.id(),
                recipient.email(), destination.id(), amount, command.description(), entry.journalEntryId(),
                entry.postedAt());
        transferRepository.save(transfer);
        return TransferView.from(transfer);
    }
}
