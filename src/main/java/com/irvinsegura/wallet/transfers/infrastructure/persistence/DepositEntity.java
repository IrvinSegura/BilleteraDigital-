package com.irvinsegura.wallet.transfers.infrastructure.persistence;

import com.irvinsegura.wallet.shared.domain.Money;
import com.irvinsegura.wallet.transfers.domain.model.Deposit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.springframework.data.domain.Persistable;

/**
 * Insert-only, like {@link TransferEntity}.
 */
@Entity
@Immutable
@Table(name = "deposits")
class DepositEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "balance_after_minor", nullable = false)
    private long balanceAfterMinor;

    @Column(name = "journal_entry_id", nullable = false)
    private UUID journalEntryId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DepositEntity() {
    }

    static DepositEntity fromDomain(Deposit deposit) {
        DepositEntity entity = new DepositEntity();
        entity.id = deposit.id();
        entity.userId = deposit.userId();
        entity.walletId = deposit.walletId();
        entity.amountMinor = deposit.amount().minorUnits();
        entity.currency = deposit.amount().currency().getCurrencyCode();
        entity.balanceAfterMinor = deposit.balanceAfter().minorUnits();
        entity.journalEntryId = deposit.journalEntryId();
        entity.createdAt = deposit.createdAt();
        return entity;
    }

    Deposit toDomain() {
        return new Deposit(id, userId, walletId, Money.ofMinor(amountMinor, currency),
                Money.ofMinor(balanceAfterMinor, currency), journalEntryId, createdAt);
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
