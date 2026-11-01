package com.irvinsegura.wallet.payments.infrastructure.persistence;

import com.irvinsegura.wallet.payments.domain.model.Payment;
import com.irvinsegura.wallet.payments.domain.model.PaymentStatus;
import com.irvinsegura.wallet.shared.domain.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
class PaymentEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "amount_minor", nullable = false, updatable = false)
    private long amountMinor;

    @Column(nullable = false, updatable = false, length = 3)
    private String currency;

    @Column(nullable = false, updatable = false, length = 140)
    private String description;

    @Column(nullable = false, updatable = false, length = 20)
    private String provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "provider_reference")
    private String providerReference;

    @Column(name = "checkout_url", length = 2048)
    private String checkoutUrl;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "provider_payment_id")
    private String providerPaymentId;

    @Column(name = "provider_refund_id")
    private String providerRefundId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected PaymentEntity() {
    }

    static PaymentEntity fromDomain(Payment payment) {
        PaymentEntity entity = new PaymentEntity();
        entity.id = payment.id();
        entity.userId = payment.userId();
        entity.amountMinor = payment.amount().minorUnits();
        entity.currency = payment.amount().currency().getCurrencyCode();
        entity.description = payment.description();
        entity.provider = payment.provider();
        entity.status = payment.status();
        entity.providerReference = payment.providerReference();
        entity.checkoutUrl = payment.checkoutUrl();
        entity.expiresAt = payment.expiresAt();
        entity.providerPaymentId = payment.providerPaymentId();
        entity.providerRefundId = payment.providerRefundId();
        entity.createdAt = payment.createdAt();
        entity.updatedAt = payment.updatedAt();
        entity.version = payment.version();
        return entity;
    }

    Payment toDomain() {
        return Payment.restore(id, userId, Money.ofMinor(amountMinor, currency), description, provider, status,
                providerReference, checkoutUrl, expiresAt, providerPaymentId, providerRefundId, createdAt, updatedAt,
                version);
    }
}
