package com.irvinsegura.wallet.transfers.domain.model;

import com.irvinsegura.wallet.shared.domain.InvalidAmountException;
import com.irvinsegura.wallet.shared.domain.Money;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Completed money transfer between two users' wallets. It is only created once the ledger entry is recorded, in the
 * same transaction, so a transfer without its money movement cannot exist.
 */
public final class Transfer {

    public static final int MAX_DESCRIPTION_LENGTH = 140;

    private final UUID id;
    private final UUID senderId;
    private final UUID sourceWalletId;
    private final UUID recipientId;
    private final String recipientEmail;
    private final UUID destinationWalletId;
    private final Money amount;
    private final String description;
    private final UUID journalEntryId;
    private final Instant createdAt;

    private Transfer(UUID id, UUID senderId, UUID sourceWalletId, UUID recipientId, String recipientEmail,
            UUID destinationWalletId, Money amount, String description, UUID journalEntryId, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.senderId = Objects.requireNonNull(senderId, "senderId must not be null");
        this.sourceWalletId = Objects.requireNonNull(sourceWalletId, "sourceWalletId must not be null");
        this.recipientId = Objects.requireNonNull(recipientId, "recipientId must not be null");
        this.recipientEmail = Objects.requireNonNull(recipientEmail, "recipientEmail must not be null");
        this.destinationWalletId = Objects.requireNonNull(destinationWalletId, "destinationWalletId must not be null");
        this.amount = Objects.requireNonNull(amount, "amount must not be null");
        this.description = description;
        this.journalEntryId = Objects.requireNonNull(journalEntryId, "journalEntryId must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        if (senderId.equals(recipientId)) {
            throw new SelfTransferException();
        }
        if (amount.isZero()) {
            throw new InvalidAmountException("Amount must be greater than zero");
        }
        if (description != null && description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException(
                    "description must have at most %d characters".formatted(MAX_DESCRIPTION_LENGTH));
        }
    }

    public static Transfer record(UUID id, UUID senderId, UUID sourceWalletId, UUID recipientId,
            String recipientEmail, UUID destinationWalletId, Money amount, String description, UUID journalEntryId,
            Instant createdAt) {
        return new Transfer(id, senderId, sourceWalletId, recipientId, recipientEmail, destinationWalletId, amount,
                normalizeDescription(description), journalEntryId, createdAt);
    }

    public static Transfer restore(UUID id, UUID senderId, UUID sourceWalletId, UUID recipientId,
            String recipientEmail, UUID destinationWalletId, Money amount, String description, UUID journalEntryId,
            Instant createdAt) {
        return new Transfer(id, senderId, sourceWalletId, recipientId, recipientEmail, destinationWalletId, amount,
                description, journalEntryId, createdAt);
    }

    public static String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        return description.strip();
    }

    public UUID id() {
        return id;
    }

    public UUID senderId() {
        return senderId;
    }

    public UUID sourceWalletId() {
        return sourceWalletId;
    }

    public UUID recipientId() {
        return recipientId;
    }

    public String recipientEmail() {
        return recipientEmail;
    }

    public UUID destinationWalletId() {
        return destinationWalletId;
    }

    public Money amount() {
        return amount;
    }

    public String description() {
        return description;
    }

    public UUID journalEntryId() {
        return journalEntryId;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
