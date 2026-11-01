package com.irvinsegura.wallet.transfers.infrastructure.persistence;

import com.irvinsegura.wallet.shared.domain.Money;
import com.irvinsegura.wallet.transfers.domain.model.Deposit;
import com.irvinsegura.wallet.transfers.domain.model.DepositStatus;
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
 * Unlike transfers and withdrawals, a deposit changes after it is created: it waits for the payment provider.
 */
@Entity
@Table(name = "deposits")
class DepositEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "wallet_id", nullable = false, updatable = false)
    private UUID walletId;

    @Column(name = "amount_minor", nullable = false, updatable = false)
    private long amountMinor;

    @Column(nullable = false, updatable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DepositStatus status;

    @Column(name = "checkout_url", length = 2048)
    private String checkoutUrl;

    @Column(name = "checkout_expires_at")
    private Instant checkoutExpiresAt;

    @Column(name = "balance_after_minor")
    private Long balanceAfterMinor;

    @Column(name = "journal_entry_id")
    private UUID journalEntryId;

    @Column(name = "rejection_reason", length = 50)
    private String rejectionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Version
    private Long version;

    protected DepositEntity() {
    }

    static DepositEntity fromDomain(Deposit deposit) {
        DepositEntity entity = new DepositEntity();
        entity.id = deposit.id();
        entity.userId = deposit.userId();
        entity.walletId = deposit.walletId();
        entity.amountMinor = deposit.amount().minorUnits();
        entity.currency = deposit.amount().currency().getCurrencyCode();
        entity.status = deposit.status();
        entity.checkoutUrl = deposit.checkoutUrl();
        entity.checkoutExpiresAt = deposit.checkoutExpiresAt();
        entity.balanceAfterMinor = deposit.balanceAfter() == null ? null : deposit.balanceAfter().minorUnits();
        entity.journalEntryId = deposit.journalEntryId();
        entity.rejectionReason = deposit.rejectionReason();
        entity.createdAt = deposit.createdAt();
        entity.closedAt = deposit.closedAt();
        entity.version = deposit.version();
        return entity;
    }

    Deposit toDomain() {
        return Deposit.restore(id, userId, walletId, Money.ofMinor(amountMinor, currency), status, checkoutUrl,
                checkoutExpiresAt, balanceAfterMinor == null ? null : Money.ofMinor(balanceAfterMinor, currency),
                journalEntryId, rejectionReason, createdAt, closedAt, version);
    }
}
