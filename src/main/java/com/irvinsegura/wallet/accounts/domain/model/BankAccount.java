package com.irvinsegura.wallet.accounts.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A user's account at a Mexican bank, where their MXN withdrawals are paid. Removing one keeps the row, because
 * withdrawals already on their way still pay into it.
 */
public class BankAccount {

    public static final int MAX_HOLDER_NAME_LENGTH = 100;
    public static final int MAX_ALIAS_LENGTH = 40;
    // Limits how many destinations someone who took over an account can add before anyone notices
    public static final int MAX_ACTIVE_PER_OWNER = 5;

    private final UUID id;
    private final UUID ownerId;
    private final Clabe clabe;
    private final String holderName;
    private final Instant createdAt;
    private final Long version;
    private Instant removedAt;
    private String alias;

    private BankAccount(UUID id, UUID ownerId, Clabe clabe, String holderName, String alias, Instant createdAt,
            Instant removedAt, Long version) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId must not be null");
        this.clabe = Objects.requireNonNull(clabe, "clabe must not be null");
        this.holderName = Objects.requireNonNull(holderName, "holderName must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.alias = alias;
        this.removedAt = removedAt;
        this.version = version;
    }

    /**
     * @param holderName the name the bank has for the account; SPEI payments carry it to the receiving bank
     */
    public static BankAccount register(UUID ownerId, Clabe clabe, String holderName, Instant now) {
        String name = holderName == null ? "" : holderName.strip();
        if (name.isEmpty() || name.length() > MAX_HOLDER_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    "holderName must have between 1 and %d characters".formatted(MAX_HOLDER_NAME_LENGTH));
        }
        return new BankAccount(UUID.randomUUID(), ownerId, clabe, name, null, now, null, null);
    }

    /**
     * Rebuilds a bank account from storage. {@code version} is the optimistic lock value; null means not persisted
     * yet.
     */
    public static BankAccount restore(UUID id, UUID ownerId, Clabe clabe, String holderName, String alias,
            Instant createdAt, Instant removedAt, Long version) {
        return new BankAccount(id, ownerId, clabe, holderName, alias, createdAt, removedAt, version);
    }

    /**
     * Names the account for its owner only; it never reaches the bank. A blank alias clears it.
     */
    public void rename(String newAlias) {
        String trimmed = newAlias == null ? "" : newAlias.strip();
        if (trimmed.length() > MAX_ALIAS_LENGTH) {
            throw new IllegalArgumentException("alias must have at most %d characters".formatted(MAX_ALIAS_LENGTH));
        }
        alias = trimmed.isEmpty() ? null : trimmed;
    }

    public boolean isActive() {
        return removedAt == null;
    }

    public boolean isOwnedBy(UUID userId) {
        return ownerId.equals(userId);
    }

    /**
     * Returns whether it was still active.
     */
    public boolean remove(Instant now) {
        if (!isActive()) {
            return false;
        }
        removedAt = Objects.requireNonNull(now, "now must not be null");
        return true;
    }

    public UUID id() {
        return id;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public Clabe clabe() {
        return clabe;
    }

    public String holderName() {
        return holderName;
    }

    public String alias() {
        return alias;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant removedAt() {
        return removedAt;
    }

    public Long version() {
        return version;
    }
}
