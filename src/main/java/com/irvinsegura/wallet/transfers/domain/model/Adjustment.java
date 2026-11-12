package com.irvinsegura.wallet.transfers.domain.model;

import com.irvinsegura.wallet.shared.domain.InvalidAmountException;
import com.irvinsegura.wallet.shared.domain.Money;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Money an administrator puts into or takes out of a customer's wallet to settle a problem, e.g. a payment the
 * provider collected that never reached the wallet. Small ones are applied by whoever requests them; above the
 * policy's limit a second administrator has to approve them, so no single person can move large sums.
 */
public class Adjustment {

    public static final int MAX_REASON = 300;

    private final UUID id;
    private final UUID walletId;
    private final UUID userId;
    private final AdjustmentDirection direction;
    private final Money amount;
    private final String reason;
    private final UUID ticketId;
    private final UUID requestedBy;
    private final Instant requestedAt;
    private AdjustmentStatus status;
    private UUID decidedBy;
    private Instant decidedAt;
    private String rejectionReason;
    private UUID journalEntryId;

    private Adjustment(UUID id, UUID walletId, UUID userId, AdjustmentDirection direction, Money amount, String reason,
            UUID ticketId, UUID requestedBy, Instant requestedAt, AdjustmentStatus status, UUID decidedBy,
            Instant decidedAt, String rejectionReason, UUID journalEntryId) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.walletId = Objects.requireNonNull(walletId, "walletId must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.direction = Objects.requireNonNull(direction, "direction must not be null");
        this.amount = Objects.requireNonNull(amount, "amount must not be null");
        this.reason = Objects.requireNonNull(reason, "reason must not be null");
        this.ticketId = ticketId;
        this.requestedBy = Objects.requireNonNull(requestedBy, "requestedBy must not be null");
        this.requestedAt = Objects.requireNonNull(requestedAt, "requestedAt must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.decidedBy = decidedBy;
        this.decidedAt = decidedAt;
        this.rejectionReason = rejectionReason;
        this.journalEntryId = journalEntryId;
    }

    /**
     * A new adjustment waiting to be applied, by its requester or by a second administrator.
     *
     * @param reason   what happened, as the customer will read it
     * @param ticketId the support ticket it settles, if any
     */
    public static Adjustment request(UUID walletId, UUID userId, AdjustmentDirection direction, Money amount,
            String reason, UUID ticketId, UUID requestedBy, Instant at) {
        if (amount.isZero()) {
            throw new InvalidAmountException("Amount must be greater than zero");
        }
        String text = reason == null ? "" : reason.strip();
        if (text.isEmpty() || text.length() > MAX_REASON) {
            throw new IllegalArgumentException("An adjustment needs a reason of up to " + MAX_REASON + " characters");
        }
        return new Adjustment(UUID.randomUUID(), walletId, userId, direction, amount, text, ticketId, requestedBy, at,
                AdjustmentStatus.PENDING_APPROVAL, null, null, null, null);
    }

    public static Adjustment restore(UUID id, UUID walletId, UUID userId, AdjustmentDirection direction, Money amount,
            String reason, UUID ticketId, UUID requestedBy, Instant requestedAt, AdjustmentStatus status,
            UUID decidedBy, Instant decidedAt, String rejectionReason, UUID journalEntryId) {
        return new Adjustment(id, walletId, userId, direction, amount, reason, ticketId, requestedBy, requestedAt,
                status, decidedBy, decidedAt, rejectionReason, journalEntryId);
    }

    /**
     * Records that the money moved, with the journal entry that moved it.
     *
     * @param secondApproval whether the policy asked for a second administrator; if so, it can't be the requester
     * @throws AdjustmentNotPendingException    if it was already applied or rejected
     * @throws AdjustmentSelfApprovalException if the requester tries to approve what needs a second person
     */
    public void apply(UUID administratorId, boolean secondApproval, UUID journalEntryId, Instant at) {
        requirePending();
        if (secondApproval && requestedBy.equals(administratorId)) {
            throw new AdjustmentSelfApprovalException();
        }
        this.status = AdjustmentStatus.APPLIED;
        this.decidedBy = administratorId;
        this.decidedAt = at;
        this.journalEntryId = Objects.requireNonNull(journalEntryId, "journalEntryId must not be null");
    }

    /**
     * Turns it down; nothing moved. The requester may withdraw their own.
     *
     * @throws AdjustmentNotPendingException if it was already applied or rejected
     */
    public void reject(UUID administratorId, String why, Instant at) {
        requirePending();
        String text = why == null ? "" : why.strip();
        if (text.isEmpty() || text.length() > MAX_REASON) {
            throw new IllegalArgumentException("A rejection needs a reason of up to " + MAX_REASON + " characters");
        }
        this.status = AdjustmentStatus.REJECTED;
        this.decidedBy = administratorId;
        this.decidedAt = at;
        this.rejectionReason = text;
    }

    /**
     * Checks it can still be decided, before any money is touched.
     *
     * @throws AdjustmentNotPendingException if it was already applied or rejected
     */
    public void requirePending() {
        if (status != AdjustmentStatus.PENDING_APPROVAL) {
            throw new AdjustmentNotPendingException();
        }
    }

    public UUID id() {
        return id;
    }

    public UUID walletId() {
        return walletId;
    }

    public UUID userId() {
        return userId;
    }

    public AdjustmentDirection direction() {
        return direction;
    }

    public Money amount() {
        return amount;
    }

    public String reason() {
        return reason;
    }

    public UUID ticketId() {
        return ticketId;
    }

    public UUID requestedBy() {
        return requestedBy;
    }

    public Instant requestedAt() {
        return requestedAt;
    }

    public AdjustmentStatus status() {
        return status;
    }

    public UUID decidedBy() {
        return decidedBy;
    }

    public Instant decidedAt() {
        return decidedAt;
    }

    public String rejectionReason() {
        return rejectionReason;
    }

    public UUID journalEntryId() {
        return journalEntryId;
    }
}
