package com.irvinsegura.wallet.transfers.domain.model;

import com.irvinsegura.wallet.shared.domain.Money;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Money a user adds to their wallet through a payment provider. It is credited only once the provider confirms the
 * payment; until then nothing is recorded in the ledger. {@code balanceAfter} is kept so a replayed request returns
 * exactly the original response.
 */
public class Deposit {

    private final UUID id;
    private final UUID userId;
    private final UUID walletId;
    private final Money amount;
    private final Instant createdAt;
    private final Long version;
    private DepositStatus status;
    private String checkoutUrl;
    private Instant checkoutExpiresAt;
    private Money balanceAfter;
    private UUID journalEntryId;
    private String rejectionReason;
    private Instant closedAt;

    private Deposit(UUID id, UUID userId, UUID walletId, Money amount, DepositStatus status, String checkoutUrl,
            Instant checkoutExpiresAt, Money balanceAfter, UUID journalEntryId, String rejectionReason,
            Instant createdAt, Instant closedAt, Long version) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.walletId = Objects.requireNonNull(walletId, "walletId must not be null");
        this.amount = Objects.requireNonNull(amount, "amount must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.checkoutUrl = checkoutUrl;
        this.checkoutExpiresAt = checkoutExpiresAt;
        this.balanceAfter = balanceAfter;
        this.journalEntryId = journalEntryId;
        this.rejectionReason = rejectionReason;
        this.closedAt = closedAt;
        this.version = version;
    }

    public static Deposit open(UUID id, UUID userId, UUID walletId, Money amount, Instant now) {
        return new Deposit(id, userId, walletId, amount, DepositStatus.PENDING, null, null, null, null, null, now,
                null, null);
    }

    /**
     * Rebuilds a deposit from storage. {@code version} is the optimistic lock value; null means not persisted yet.
     */
    public static Deposit restore(UUID id, UUID userId, UUID walletId, Money amount, DepositStatus status,
            String checkoutUrl, Instant checkoutExpiresAt, Money balanceAfter, UUID journalEntryId,
            String rejectionReason, Instant createdAt, Instant closedAt, Long version) {
        return new Deposit(id, userId, walletId, amount, status, checkoutUrl, checkoutExpiresAt, balanceAfter,
                journalEntryId, rejectionReason, createdAt, closedAt, version);
    }

    public boolean isPending() {
        return status == DepositStatus.PENDING;
    }

    /**
     * Where the payer completes the payment; only while pending.
     */
    public void awaitPayment(String url, Instant expiresAt) {
        requirePending();
        checkoutUrl = Objects.requireNonNull(url, "url must not be null");
        checkoutExpiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }

    public void complete(Money newBalance, UUID entryId, Instant now) {
        requirePending();
        balanceAfter = Objects.requireNonNull(newBalance, "newBalance must not be null");
        journalEntryId = Objects.requireNonNull(entryId, "entryId must not be null");
        close(DepositStatus.COMPLETED, now);
    }

    /**
     * @param reason the code of the rule that refused the money, e.g. {@code WALLET_NOT_ACTIVE}
     */
    public void reject(String reason, Instant now) {
        requirePending();
        rejectionReason = Objects.requireNonNull(reason, "reason must not be null");
        close(DepositStatus.REJECTED, now);
    }

    /**
     * Ends a deposit that will never be paid. Returns whether it was still pending.
     */
    public boolean abandon(DepositStatus finalStatus, Instant now) {
        if (finalStatus != DepositStatus.EXPIRED && finalStatus != DepositStatus.FAILED) {
            throw new IllegalArgumentException("A deposit can only be abandoned as expired or failed");
        }
        if (!isPending()) {
            return false;
        }
        close(finalStatus, now);
        return true;
    }

    private void close(DepositStatus finalStatus, Instant now) {
        status = finalStatus;
        closedAt = Objects.requireNonNull(now, "now must not be null");
        checkoutUrl = null;
    }

    private void requirePending() {
        if (!isPending()) {
            throw new IllegalStateException("Deposit " + id + " is already " + status);
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

    public Money amount() {
        return amount;
    }

    public DepositStatus status() {
        return status;
    }

    public String checkoutUrl() {
        return checkoutUrl;
    }

    public Instant checkoutExpiresAt() {
        return checkoutExpiresAt;
    }

    /**
     * Null until completed.
     */
    public Money balanceAfter() {
        return balanceAfter;
    }

    /**
     * Null until completed.
     */
    public UUID journalEntryId() {
        return journalEntryId;
    }

    public String rejectionReason() {
        return rejectionReason;
    }

    public Instant createdAt() {
        return createdAt;
    }

    /**
     * When it left the pending state; null while pending.
     */
    public Instant closedAt() {
        return closedAt;
    }

    public Long version() {
        return version;
    }
}
