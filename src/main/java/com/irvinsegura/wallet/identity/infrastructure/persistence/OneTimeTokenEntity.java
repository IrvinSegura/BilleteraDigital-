package com.irvinsegura.wallet.identity.infrastructure.persistence;

import com.irvinsegura.wallet.identity.domain.model.OneTimeToken;
import com.irvinsegura.wallet.identity.domain.model.TokenPurpose;
import com.irvinsegura.wallet.identity.domain.model.UserId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

// Rows are only inserted and deleted, never updated, so there is no version column
@Entity
@Table(name = "one_time_tokens")
class OneTimeTokenEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 30)
    private TokenPurpose purpose;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    protected OneTimeTokenEntity() {
    }

    static OneTimeTokenEntity fromDomain(OneTimeToken token) {
        OneTimeTokenEntity entity = new OneTimeTokenEntity();
        entity.id = token.id();
        entity.userId = token.userId().value();
        entity.purpose = token.purpose();
        entity.tokenHash = token.tokenHash();
        entity.createdAt = token.createdAt();
        entity.expiresAt = token.expiresAt();
        return entity;
    }

    OneTimeToken toDomain() {
        return OneTimeToken.restore(id, new UserId(userId), purpose, tokenHash, createdAt, expiresAt);
    }
}
