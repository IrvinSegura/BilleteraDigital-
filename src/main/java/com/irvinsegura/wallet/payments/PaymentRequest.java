package com.irvinsegura.wallet.payments;

import com.irvinsegura.wallet.shared.domain.Money;
import java.util.Objects;
import java.util.UUID;

/**
 * @param paymentId   chosen by the caller, usually the id of the operation being paid for, so both share one id
 * @param description shown to the payer on the provider's checkout page
 */
public record PaymentRequest(UUID paymentId, UUID userId, Money amount, String description) {

    public PaymentRequest {
        Objects.requireNonNull(paymentId, "paymentId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(description, "description must not be null");
    }
}
