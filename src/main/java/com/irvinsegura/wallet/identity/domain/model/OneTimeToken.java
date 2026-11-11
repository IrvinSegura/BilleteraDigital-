package com.irvinsegura.wallet.identity.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Secret sent by email in a link (verify the address, reset the password). Like refresh tokens, only the SHA-256 hash
 * is stored, and the token is deleted when used, so it works once.
 */
public class OneTimeToken {

    private final UUID id;
    private final UserId userId;
    private final TokenPurpose purpose;
    private final String tokenHash;
    private final Instant createdAt;
    private final Instant expiresAt;
    private final Email newEmail;

    private OneTimeToken(UUID id, UserId userId, TokenPurpose purpose, String tokenHash, Instant createdAt,
            Instant expiresAt, Email newEmail) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.purpose = Objects.requireNonNull(purpose, "purpose must not be null");
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        if ((purpose == TokenPurpose.CHANGE_EMAIL) != (newEmail != null)) {
            throw new IllegalArgumentException("newEmail goes with CHANGE_EMAIL tokens, and only with them");
        }
        this.newEmail = newEmail;
    }

    /**
     * The raw value is returned only here, to be put in the email, and is never stored.
     */
    public static Issued issue(UserId userId, TokenPurpose purpose, Instant now, Duration ttl) {
        return issue(userId, purpose, null, now, ttl);
    }

    /**
     * A link that moves the account to {@code newEmail} once it is followed.
     */
    public static Issued issueEmailChange(UserId userId, Email newEmail, Instant now, Duration ttl) {
        return issue(userId, TokenPurpose.CHANGE_EMAIL, Objects.requireNonNull(newEmail, "newEmail must not be null"),
                now, ttl);
    }

    private static Issued issue(UserId userId, TokenPurpose purpose, Email newEmail, Instant now, Duration ttl) {
        String rawValue = OpaqueTokens.generate();
        OneTimeToken token = new OneTimeToken(UUID.randomUUID(), userId, purpose, hash(rawValue), now, now.plus(ttl),
                newEmail);
        return new Issued(token, rawValue);
    }

    /**
     * @param newEmail the address a {@code CHANGE_EMAIL} token confirms; null for the other purposes
     */
    public static OneTimeToken restore(UUID id, UserId userId, TokenPurpose purpose, String tokenHash,
            Instant createdAt, Instant expiresAt, Email newEmail) {
        return new OneTimeToken(id, userId, purpose, tokenHash, createdAt, expiresAt, newEmail);
    }

    public static String hash(String rawValue) {
        return OpaqueTokens.hash(rawValue);
    }

    /**
     * Whether the token may be used now for that purpose.
     */
    public boolean isValidFor(TokenPurpose expected, Instant now) {
        return purpose == expected && now.isBefore(expiresAt);
    }

    public UUID id() {
        return id;
    }

    public UserId userId() {
        return userId;
    }

    public TokenPurpose purpose() {
        return purpose;
    }

    public String tokenHash() {
        return tokenHash;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Email newEmail() {
        return newEmail;
    }

    public record Issued(OneTimeToken token, String rawValue) {
    }
}
