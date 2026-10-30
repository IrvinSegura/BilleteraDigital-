package com.irvinsegura.wallet.transfers.infrastructure.persistence;

import com.irvinsegura.wallet.shared.domain.Money;
import com.irvinsegura.wallet.transfers.domain.model.Withdrawal;
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
@Table(name = "withdrawals")
class WithdrawalEntity implements Persistable<UUID> {

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

    protected WithdrawalEntity() {
    }

    static WithdrawalEntity fromDomain(Withdrawal withdrawal) {
        WithdrawalEntity entity = new WithdrawalEntity();
        entity.id = withdrawal.id();
        entity.userId = withdrawal.userId();
        entity.walletId = withdrawal.walletId();
        entity.amountMinor = withdrawal.amount().minorUnits();
        entity.currency = withdrawal.amount().currency().getCurrencyCode();
        entity.balanceAfterMinor = withdrawal.balanceAfter().minorUnits();
        entity.journalEntryId = withdrawal.journalEntryId();
        entity.createdAt = withdrawal.createdAt();
        return entity;
    }

    Withdrawal toDomain() {
        return new Withdrawal(id, userId, walletId, Money.ofMinor(amountMinor, currency),
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
