package com.irvinsegura.wallet.transfers.domain.model;

import com.irvinsegura.wallet.shared.domain.Money;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A payout to send.
 *
 * @param withdrawalId identifies the payout at the provider, which makes sending it again harmless
 * @param requestedAt  when the user asked for the withdrawal; the same on every attempt, so a provider can tell
 *                     which days to look for a payout it may have received before
 * @param destination  the bank account to pay into; null for currencies paid without one (USD)
 */
public record PayoutOrder(UUID withdrawalId, Money amount, Instant requestedAt, Destination destination) {

    public PayoutOrder {
        Objects.requireNonNull(withdrawalId, "withdrawalId must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(requestedAt, "requestedAt must not be null");
    }

    /**
     * @param holderName SPEI carries it to the receiving bank, which may refuse a payout whose name does not match
     */
    public record Destination(String clabe, String holderName) {

        public Destination {
            Objects.requireNonNull(clabe, "clabe must not be null");
            Objects.requireNonNull(holderName, "holderName must not be null");
        }

        @Override
        public String toString() {
            return "CLABE ending " + clabe.substring(clabe.length() - 4);
        }
    }
}
