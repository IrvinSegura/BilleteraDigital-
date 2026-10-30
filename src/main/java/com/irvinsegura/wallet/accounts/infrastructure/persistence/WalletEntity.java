package com.irvinsegura.wallet.accounts.infrastructure.persistence;

import com.irvinsegura.wallet.accounts.domain.model.Wallet;
import com.irvinsegura.wallet.accounts.domain.model.WalletStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

@Entity
@Table(name = "wallets")
class WalletEntity {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(nullable = false, updatable = false, length = 3)
    private String currency;

    @Column(name = "ledger_account_id", nullable = false, updatable = false)
    private UUID ledgerAccountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WalletStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    private Long version;

    protected WalletEntity() {
    }

    static WalletEntity fromDomain(Wallet wallet) {
        WalletEntity entity = new WalletEntity();
        entity.id = wallet.id();
        entity.ownerId = wallet.ownerId();
        entity.currency = wallet.currency().getCurrencyCode();
        entity.ledgerAccountId = wallet.ledgerAccountId();
        entity.status = wallet.status();
        entity.createdAt = wallet.createdAt();
        entity.version = wallet.version();
        return entity;
    }

    Wallet toDomain() {
        return Wallet.restore(id, ownerId, Currency.getInstance(currency), ledgerAccountId, status, createdAt,
                version);
    }
}
