package com.irvinsegura.wallet.ledger.infrastructure.persistence;

import com.irvinsegura.wallet.ledger.domain.model.AccountType;
import com.irvinsegura.wallet.ledger.domain.model.LedgerAccount;
import com.irvinsegura.wallet.shared.domain.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_accounts")
class LedgerAccountEntity {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private AccountType type;

    @Column(nullable = false, updatable = false, length = 3)
    private String currency;

    @Column(name = "balance_minor", nullable = false)
    private long balanceMinor;

    @Column(name = "system_code", updatable = false, length = 30)
    private String systemCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    private Long version;

    protected LedgerAccountEntity() {
    }

    static LedgerAccountEntity fromDomain(LedgerAccount account) {
        LedgerAccountEntity entity = new LedgerAccountEntity();
        entity.id = account.id();
        entity.type = account.type();
        entity.currency = account.currency().getCurrencyCode();
        entity.balanceMinor = account.balance().minorUnits();
        entity.systemCode = account.systemCode();
        entity.createdAt = account.createdAt();
        entity.version = account.version();
        return entity;
    }

    LedgerAccount toDomain() {
        return LedgerAccount.restore(id, type, Money.ofMinor(balanceMinor, currency), systemCode, createdAt,
                version);
    }
}
