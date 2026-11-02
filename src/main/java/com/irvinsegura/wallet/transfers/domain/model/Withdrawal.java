package com.irvinsegura.wallet.transfers.domain.model;

import com.irvinsegura.wallet.shared.domain.Money;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Payout to the user's bank. The money leaves the wallet when it is requested and waits in transit until the
 * provider confirms or refuses the payout. {@code balanceAfter} is the wallet balance right after the money left, kept
 * so a replayed request returns exactly the original response.
 */
public class Withdrawal {

    private final UUID id;
    private final UUID userId;
    private final UUID walletId;
    private final UUID bankAccountId;
    private final Money amount;
    private final Money balanceAfter;
    private final UUID journalEntryId;
    private final Instant createdAt;
    private final Long version;
    private WithdrawalStatus status;
    private String providerReference;
    private UUID settlementEntryId;
    private String failureReason;
    private Instant closedAt;

    private Withdrawal(UUID id, UUID userId, UUID walletId, UUID bankAccountId, Money amount, Money balanceAfter,
            UUID journalEntryId, WithdrawalStatus status, String providerReference, UUID settlementEntryId, String failureReason,
            Instant createdAt, Instant closedAt, Long version) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.walletId = Objects.requireNonNull(walletId, "walletId must not be null");
        this.bankAccountId = bankAccountId;
        this.amount = Objects.requireNonNull(amount, "amount must not be null");
        this.balanceAfter = Objects.requireNonNull(balanceAfter, "balanceAfter must not be null");
        this.journalEntryId = Objects.requireNonNull(journalEntryId, "journalEntryId must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.providerReference = providerReference;
        this.settlementEntryId = settlementEntryId;
        this.failureReason = failureReason;
        this.closedAt = closedAt;
        this.version = version;
    }

    /**
     * @param bankAccountId  where the payout goes; null for currencies paid without one
     * @param journalEntryId the entry that moved the money from the wallet into transit
     */
    public static Withdrawal request(UUID id, UUID userId, UUID walletId, UUID bankAccountId, Money amount,
            Money balanceAfter, UUID journalEntryId, Instant now) {
        return new Withdrawal(id, userId, walletId, bankAccountId, amount, balanceAfter, journalEntryId,
                WithdrawalStatus.PENDING, null, null, null, now, null, null);
    }

    /**
     * Rebuilds a withdrawal from storage. {@code version} is the optimistic lock value; null means not persisted yet.
     */
    public static Withdrawal restore(UUID id, UUID userId, UUID walletId, UUID bankAccountId, Money amount,
            Money balanceAfter, UUID journalEntryId, WithdrawalStatus status, String providerReference,
            UUID settlementEntryId, String failureReason, Instant createdAt, Instant closedAt, Long version) {
        return new Withdrawal(id, userId, walletId, bankAccountId, amount, balanceAfter, journalEntryId, status,
                providerReference, settlementEntryId, failureReason, createdAt, closedAt, version);
    }

    public boolean isPending() {
        return status == WithdrawalStatus.PENDING;
    }

    /**
     * Records that the provider accepted the payout. Returns whether anything changed.
     */
    public boolean markSent(String reference) {
        requirePending();
        if (Objects.equals(providerReference, reference)) {
            return false;
        }
        providerReference = Objects.requireNonNull(reference, "reference must not be null");
        return true;
    }

    /**
     * @param entryId the entry that moved the money from transit to the settlement account
     */
    public void markPaid(String reference, UUID entryId, Instant now) {
        close(WithdrawalStatus.PAID, reference, entryId, now);
    }

    /**
     * @param entryId the entry that gave the money back to the wallet
     */
    public void fail(String reference, String reason, UUID entryId, Instant now) {
        failureReason = Objects.requireNonNull(reason, "reason must not be null");
        close(WithdrawalStatus.FAILED, reference, entryId, now);
    }

    private void close(WithdrawalStatus finalStatus, String reference, UUID entryId, Instant now) {
        requirePending();
        providerReference = Objects.requireNonNull(reference, "reference must not be null");
        settlementEntryId = Objects.requireNonNull(entryId, "entryId must not be null");
        closedAt = Objects.requireNonNull(now, "now must not be null");
        status = finalStatus;
    }

    private void requirePending() {
        if (!isPending()) {
            throw new IllegalStateException("Withdrawal " + id + " is already " + status);
        }
    }

    public UUID id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public UUID walletId() {
        return walletId;
    }

    /**
     * Null for currencies paid without a bank account, and for withdrawals made before bank accounts existed.
     */
    public UUID bankAccountId() {
        return bankAccountId;
    }

    public Money amount() {
        return amount;
    }

    public Money balanceAfter() {
        return balanceAfter;
    }

    public UUID journalEntryId() {
        return journalEntryId;
    }

    public WithdrawalStatus status() {
        return status;
    }

    /**
     * Null until the provider accepted the payout.
     */
    public String providerReference() {
        return providerReference;
    }

    /**
     * Null while pending, and for withdrawals paid before payouts were asynchronous.
     */
    public UUID settlementEntryId() {
        return settlementEntryId;
    }

    public String failureReason() {
        return failureReason;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant closedAt() {
        return closedAt;
    }

    public Long version() {
        return version;
    }
}
