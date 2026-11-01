package com.irvinsegura.wallet.identity.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Opaque, single-use refresh token. Every login starts a family; each refresh revokes the presented token and
 * issues a new one in the same family. Only the SHA-256 hash is stored, so a database leak exposes no usable token.
 */
public class RefreshToken {

    private final UUID id;
    private final UserId userId;
    private final UUID familyId;
    private final String tokenHash;
    private final Instant issuedAt;
    private final Instant expiresAt;
    private final Long version;
    private Instant revokedAt;

    private RefreshToken(UUID id, UserId userId, UUID familyId, String tokenHash, Instant issuedAt, Instant expiresAt,
            Instant revokedAt, Long version) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.familyId = Objects.requireNonNull(familyId, "familyId must not be null");
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash must not be null");
        this.issuedAt = Objects.requireNonNull(issuedAt, "issuedAt must not be null");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        this.revokedAt = revokedAt;
        this.version = version;
    }

    /**
     * Creates a token in the given family. The raw value is returned only here and is never stored.
     */
    public static Issued issue(UserId userId, UUID familyId, Instant now, Duration ttl) {
        String rawValue = OpaqueTokens.generate();
        RefreshToken token = new RefreshToken(
                UUID.randomUUID(), userId, familyId, hash(rawValue), now, now.plus(ttl), null, null);
        return new Issued(token, rawValue);
    }

    /**
     * Rebuilds a token from storage. {@code version} is the optimistic lock value; null means not persisted yet.
     */
    public static RefreshToken restore(UUID id, UserId userId, UUID familyId, String tokenHash, Instant issuedAt,
            Instant expiresAt, Instant revokedAt, Long version) {
        return new RefreshToken(id, userId, familyId, tokenHash, issuedAt, expiresAt, revokedAt, version);
    }

    public static String hash(String rawValue) {
        return OpaqueTokens.hash(rawValue);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpiredAt(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }

    public UUID id() {
        return id;
    }

    public UserId userId() {
        return userId;
    }

    public UUID familyId() {
        return familyId;
    }

    public String tokenHash() {
        return tokenHash;
    }

    public Instant issuedAt() {
        return issuedAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant revokedAt() {
        return revokedAt;
    }

    public Long version() {
        return version;
    }

    public record Issued(RefreshToken token, String rawValue) {
    }
}
