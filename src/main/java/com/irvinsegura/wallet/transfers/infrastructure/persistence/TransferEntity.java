package com.irvinsegura.wallet.transfers.infrastructure.persistence;

import com.irvinsegura.wallet.shared.domain.Money;
import com.irvinsegura.wallet.transfers.domain.model.Transfer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.springframework.data.domain.Persistable;

/**
 * Insert-only: completed transfers never change. {@code isNew} always being true makes {@code save} insert without a
 * prior select.
 */
@Entity
@Immutable
@Table(name = "transfers")
class TransferEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    @Column(name = "source_wallet_id", nullable = false)
    private UUID sourceWalletId;

    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;

    @Column(name = "recipient_email", nullable = false, length = 254)
    private String recipientEmail;

    @Column(name = "destination_wallet_id", nullable = false)
    private UUID destinationWalletId;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(length = 140)
    private String description;

    @Column(name = "journal_entry_id", nullable = false)
    private UUID journalEntryId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TransferEntity() {
    }

    static TransferEntity fromDomain(Transfer transfer) {
        TransferEntity entity = new TransferEntity();
        entity.id = transfer.id();
        entity.senderId = transfer.senderId();
        entity.sourceWalletId = transfer.sourceWalletId();
        entity.recipientId = transfer.recipientId();
        entity.recipientEmail = transfer.recipientEmail();
        entity.destinationWalletId = transfer.destinationWalletId();
        entity.amountMinor = transfer.amount().minorUnits();
        entity.currency = transfer.amount().currency().getCurrencyCode();
        entity.description = transfer.description();
        entity.journalEntryId = transfer.journalEntryId();
        entity.createdAt = transfer.createdAt();
        return entity;
    }

    Transfer toDomain() {
        return Transfer.restore(id, senderId, sourceWalletId, recipientId, recipientEmail, destinationWalletId,
                Money.ofMinor(amountMinor, currency), description, journalEntryId, createdAt);
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return true;
    }
}
