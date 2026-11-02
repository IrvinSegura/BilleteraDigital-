package com.irvinsegura.wallet.transfers.domain.model;

import java.util.Objects;

/**
 * What the payout provider reports about a payout.
 *
 * @param reference     the provider's id for the payout, e.g. a SPEI tracking key
 * @param failureReason the provider's code for why it was refused; set only when failed
 */
public record PayoutResult(String reference, Status status, String failureReason) {

    public PayoutResult {
        Objects.requireNonNull(reference, "reference must not be null");
        Objects.requireNonNull(status, "status must not be null");
        if (status == Status.FAILED) {
            Objects.requireNonNull(failureReason, "failureReason must not be null when failed");
        }
    }

    public static PayoutResult pending(String reference) {
        return new PayoutResult(reference, Status.PENDING, null);
    }

    public static PayoutResult paid(String reference) {
        return new PayoutResult(reference, Status.PAID, null);
    }

    public static PayoutResult failed(String reference, String failureReason) {
        return new PayoutResult(reference, Status.FAILED, failureReason);
    }

    public enum Status {
        // Accepted, on its way to the bank
        PENDING,
        PAID,
        FAILED
    }
}
