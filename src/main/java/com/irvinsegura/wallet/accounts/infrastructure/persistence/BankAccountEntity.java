package com.irvinsegura.wallet.accounts.infrastructure.persistence;

import com.irvinsegura.wallet.accounts.domain.model.BankAccount;
import com.irvinsegura.wallet.accounts.domain.model.Clabe;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bank_accounts")
class BankAccountEntity {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(nullable = false, updatable = false, length = 18)
    private String clabe;

    @Column(name = "holder_name", nullable = false, updatable = false, length = 100)
    private String holderName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "removed_at")
    private Instant removedAt;

    @Version
    private Long version;

    protected BankAccountEntity() {
    }

    static BankAccountEntity fromDomain(BankAccount bankAccount) {
        BankAccountEntity entity = new BankAccountEntity();
        entity.id = bankAccount.id();
        entity.ownerId = bankAccount.ownerId();
        entity.clabe = bankAccount.clabe().value();
        entity.holderName = bankAccount.holderName();
        entity.createdAt = bankAccount.createdAt();
        entity.removedAt = bankAccount.removedAt();
        entity.version = bankAccount.version();
        return entity;
    }

    BankAccount toDomain() {
        return BankAccount.restore(id, ownerId, new Clabe(clabe), holderName, createdAt, removedAt, version);
    }
}
