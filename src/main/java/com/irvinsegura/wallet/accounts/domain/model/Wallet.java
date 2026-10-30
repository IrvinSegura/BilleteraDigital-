package com.irvinsegura.wallet.accounts.domain.model;

import java.time.Instant;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

/**
 * A user's wallet in one currency. The money itself lives in the ledger account it points to.
 */
public class Wallet {

    private final UUID id;
    private final UUID ownerId;
    private final Currency currency;
    private final UUID ledgerAccountId;
    private final Instant createdAt;
    private final Long version;
    private WalletStatus status;

    private Wallet(UUID id, UUID ownerId, Currency currency, UUID ledgerAccountId, WalletStatus status,
            Instant createdAt, Long version) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId must not be null");
        this.currency = Objects.requireNonNull(currency, "currency must not be null");
        this.ledgerAccountId = Objects.requireNonNull(ledgerAccountId, "ledgerAccountId must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.version = version;
    }

    public static Wallet open(UUID ownerId, Currency currency, UUID ledgerAccountId, Instant now) {
        return new Wallet(UUID.randomUUID(), ownerId, currency, ledgerAccountId, WalletStatus.ACTIVE, now, null);
    }

    /**
     * Rebuilds a wallet from storage. {@code version} is the optimistic lock value; null means not persisted yet.
     */
    public static Wallet restore(UUID id, UUID ownerId, Currency currency, UUID ledgerAccountId, WalletStatus status,
            Instant createdAt, Long version) {
        return new Wallet(id, ownerId, currency, ledgerAccountId, status, createdAt, version);
    }

    public boolean isOwnedBy(UUID userId) {
        return ownerId.equals(userId);
    }

    public boolean isActive() {
        return status == WalletStatus.ACTIVE;
    }

    /**
     * Stops all money movement in and out of the wallet. Returns whether the status changed, so freezing twice
     * records a single event.
     */
    public boolean freeze() {
        return changeStatus(WalletStatus.FROZEN);
    }

    public boolean unfreeze() {
        return changeStatus(WalletStatus.ACTIVE);
    }

    private boolean changeStatus(WalletStatus newStatus) {
        if (status == newStatus) {
            return false;
        }
        status = newStatus;
        return true;
    }

    public UUID id() {
        return id;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public Currency currency() {
        return currency;
    }

    public UUID ledgerAccountId() {
        return ledgerAccountId;
    }

    public WalletStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Long version() {
        return version;
    }
}
