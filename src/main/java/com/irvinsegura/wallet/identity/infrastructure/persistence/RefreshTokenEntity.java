package com.irvinsegura.wallet.identity.infrastructure.persistence;

import com.irvinsegura.wallet.identity.domain.model.ClientInfo;
import com.irvinsegura.wallet.identity.domain.model.RefreshToken;
import com.irvinsegura.wallet.identity.domain.model.UserId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
class RefreshTokenEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "user_agent", updatable = false)
    private String userAgent;

    @Column(name = "ip_address", updatable = false, length = 45)
    private String ipAddress;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Version
    private Long version;

    protected RefreshTokenEntity() {
    }

    static RefreshTokenEntity fromDomain(RefreshToken token) {
        RefreshTokenEntity entity = new RefreshTokenEntity();
        entity.id = token.id();
        entity.userId = token.userId().value();
        entity.familyId = token.familyId();
        entity.tokenHash = token.tokenHash();
        entity.issuedAt = token.issuedAt();
        entity.expiresAt = token.expiresAt();
        entity.userAgent = token.client().userAgent();
        entity.ipAddress = token.client().ipAddress();
        entity.revokedAt = token.revokedAt();
        entity.version = token.version();
        return entity;
    }

    RefreshToken toDomain() {
        return RefreshToken.restore(id, new UserId(userId), familyId, tokenHash, issuedAt, expiresAt,
                new ClientInfo(userAgent, ipAddress), revokedAt, version);
    }
}
