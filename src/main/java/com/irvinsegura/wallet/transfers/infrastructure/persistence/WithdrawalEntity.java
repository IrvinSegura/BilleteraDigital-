package com.irvinsegura.wallet.transfers.infrastructure.persistence;

import com.irvinsegura.wallet.shared.domain.Money;
import com.irvinsegura.wallet.transfers.domain.model.Withdrawal;
import com.irvinsegura.wallet.transfers.domain.model.WithdrawalStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * Changes after it is created, like {@link DepositEntity}: it waits for the payout provider.
 */
@Entity
@Table(name = "withdrawals")
class WithdrawalEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "wallet_id", nullable = false, updatable = false)
    private UUID walletId;

    @Column(name = "bank_account_id", updatable = false)
    private UUID bankAccountId;

    @Column(name = "amount_minor", nullable = false, updatable = false)
    private long amountMinor;

    @Column(nullable = false, updatable = false, length = 3)
    private String currency;

    @Column(name = "balance_after_minor", nullable = false, updatable = false)
    private long balanceAfterMinor;

    @Column(name = "journal_entry_id", nullable = false, updatable = false)
    private UUID journalEntryId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WithdrawalStatus status;

    @Column(name = "provider_reference")
    private String providerReference;

    @Column(name = "settlement_entry_id")
    private UUID settlementEntryId;

    @Column(name = "failure_reason", length = 50)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Version
    private Long version;

    protected WithdrawalEntity() {
    }

    static WithdrawalEntity fromDomain(Withdrawal withdrawal) {
        WithdrawalEntity entity = new WithdrawalEntity();
        entity.id = withdrawal.id();
        entity.userId = withdrawal.userId();
        entity.walletId = withdrawal.walletId();
        entity.bankAccountId = withdrawal.bankAccountId();
        entity.amountMinor = withdrawal.amount().minorUnits();
        entity.currency = withdrawal.amount().currency().getCurrencyCode();
        entity.balanceAfterMinor = withdrawal.balanceAfter().minorUnits();
        entity.journalEntryId = withdrawal.journalEntryId();
        entity.status = withdrawal.status();
        entity.providerReference = withdrawal.providerReference();
        entity.settlementEntryId = withdrawal.settlementEntryId();
        entity.failureReason = withdrawal.failureReason();
        entity.createdAt = withdrawal.createdAt();
        entity.closedAt = withdrawal.closedAt();
        entity.version = withdrawal.version();
        return entity;
    }

    Withdrawal toDomain() {
        return Withdrawal.restore(id, userId, walletId, bankAccountId, Money.ofMinor(amountMinor, currency),
                Money.ofMinor(balanceAfterMinor, currency), journalEntryId, status, providerReference,
                settlementEntryId, failureReason, createdAt, closedAt, version);
    }
}
