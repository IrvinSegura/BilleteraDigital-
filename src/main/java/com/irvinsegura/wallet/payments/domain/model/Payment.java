package com.irvinsegura.wallet.payments.domain.model;

import com.irvinsegura.wallet.shared.domain.Money;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Money collected from a user through a payment provider. Every transition returns whether anything changed, so a
 * webhook delivered twice, or a webhook racing the job that checks stale payments, takes effect once.
 */
public class Payment {

    // Money was collected in these states
    private static final Set<PaymentStatus> COLLECTED =
            EnumSet.of(PaymentStatus.SUCCEEDED, PaymentStatus.REFUND_PENDING, PaymentStatus.REFUNDED);

    private final UUID id;
    private final UUID userId;
    private final Money amount;
    private final String description;
    private final String provider;
    private final Instant createdAt;
    private final Long version;
    private PaymentStatus status;
    private String providerReference;
    private String checkoutUrl;
    private Instant expiresAt;
    private String providerPaymentId;
    private String providerRefundId;
    private Instant updatedAt;

    private Payment(UUID id, UUID userId, Money amount, String description, String provider, PaymentStatus status,
            String providerReference, String checkoutUrl, Instant expiresAt, String providerPaymentId,
            String providerRefundId, Instant createdAt, Instant updatedAt, Long version) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.amount = Objects.requireNonNull(amount, "amount must not be null");
        this.description = Objects.requireNonNull(description, "description must not be null");
        this.provider = Objects.requireNonNull(provider, "provider must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        this.providerReference = providerReference;
        this.checkoutUrl = checkoutUrl;
        this.expiresAt = expiresAt;
        this.providerPaymentId = providerPaymentId;
        this.providerRefundId = providerRefundId;
        this.version = version;
    }

    public static Payment register(UUID id, UUID userId, Money amount, String description, String provider,
            Instant now) {
        if (amount.isZero() || amount.minorUnits() < 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        return new Payment(id, userId, amount, description, provider, PaymentStatus.CREATED, null, null, null, null,
                null, now, now, null);
    }

    /**
     * Rebuilds a payment from storage. {@code version} is the optimistic lock value; null means not persisted yet.
     */
    public static Payment restore(UUID id, UUID userId, Money amount, String description, String provider,
            PaymentStatus status, String providerReference, String checkoutUrl, Instant expiresAt,
            String providerPaymentId, String providerRefundId, Instant createdAt, Instant updatedAt, Long version) {
        return new Payment(id, userId, amount, description, provider, status, providerReference, checkoutUrl,
                expiresAt, providerPaymentId, providerRefundId, createdAt, updatedAt, version);
    }

    /**
     * Links the checkout the provider just opened. A checkout paid on creation succeeds right away.
     */
    public void open(CheckoutSession session, Instant now) {
        if (status != PaymentStatus.CREATED) {
            throw new IllegalStateException("Payment " + id + " was already started");
        }
        providerReference = session.reference();
        checkoutUrl = session.url();
        expiresAt = session.expiresAt();
        status = PaymentStatus.PENDING;
        updatedAt = now;
        if (session.isPaid()) {
            succeed(session.capturedPaymentId(), amount, now);
        }
    }

    /**
     * Records the collected money. Also accepted after the payment was failed or expired here: the provider has the
     * money anyway, so the caller must learn about it and give it back.
     *
     * @throws PaymentAmountMismatchException if the provider charged a different amount
     */
    public boolean succeed(String collectedPaymentId, Money paid, Instant now) {
        Objects.requireNonNull(collectedPaymentId, "collectedPaymentId must not be null");
        if (isCollected()) {
            return false;
        }
        if (!amount.equals(paid)) {
            throw new PaymentAmountMismatchException(
                    "Payment %s was registered for %s but the provider charged %s".formatted(id, amount, paid));
        }
        providerPaymentId = collectedPaymentId;
        status = PaymentStatus.SUCCEEDED;
        updatedAt = now;
        return true;
    }

    public boolean fail(Instant now) {
        return close(PaymentStatus.FAILED, now);
    }

    public boolean expire(Instant now) {
        return close(PaymentStatus.EXPIRED, now);
    }

    private boolean close(PaymentStatus finalStatus, Instant now) {
        if (status != PaymentStatus.CREATED && status != PaymentStatus.PENDING) {
            return false;
        }
        status = finalStatus;
        updatedAt = now;
        return true;
    }

    public boolean requestRefund(Instant now) {
        if (status != PaymentStatus.SUCCEEDED) {
            return false;
        }
        status = PaymentStatus.REFUND_PENDING;
        updatedAt = now;
        return true;
    }

    public boolean markRefunded(String refundId, Instant now) {
        if (status != PaymentStatus.REFUND_PENDING) {
            return false;
        }
        providerRefundId = Objects.requireNonNull(refundId, "refundId must not be null");
        status = PaymentStatus.REFUNDED;
        updatedAt = now;
        return true;
    }

    public boolean isCollected() {
        return COLLECTED.contains(status);
    }

    public UUID id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public Money amount() {
        return amount;
    }

    public String description() {
        return description;
    }

    public String provider() {
        return provider;
    }

    public PaymentStatus status() {
        return status;
    }

    public String providerReference() {
        return providerReference;
    }

    public String checkoutUrl() {
        return checkoutUrl;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public String providerPaymentId() {
        return providerPaymentId;
    }

    public String providerRefundId() {
        return providerRefundId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Long version() {
        return version;
    }
}
