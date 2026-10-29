package com.irvinsegura.wallet.identity.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RefreshTokenTest {

    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");

    private final RefreshToken.Issued issued =
            RefreshToken.issue(UserId.newId(), UUID.randomUUID(), NOW, Duration.ofDays(7));

    @Test
    void storesOnlyTheHashOfTheRawValue() {
        assertThat(issued.token().tokenHash())
                .isEqualTo(RefreshToken.hash(issued.rawValue()))
                .isNotEqualTo(issued.rawValue())
                .hasSize(64);
    }

    @Test
    void rawValuesAreUnique() {
        RefreshToken.Issued other = RefreshToken.issue(UserId.newId(), UUID.randomUUID(), NOW, Duration.ofDays(7));

        assertThat(other.rawValue()).isNotEqualTo(issued.rawValue());
    }

    @Test
    void expiresAtTheEndOfItsLifetime() {
        assertThat(issued.token().isExpiredAt(NOW.plus(Duration.ofDays(7)).minusMillis(1))).isFalse();
        assertThat(issued.token().isExpiredAt(NOW.plus(Duration.ofDays(7)))).isTrue();
    }

    @Test
    void revokingKeepsTheFirstRevocationTime() {
        RefreshToken token = issued.token();

        token.revoke(NOW);
        token.revoke(NOW.plusSeconds(60));

        assertThat(token.isRevoked()).isTrue();
        assertThat(token.revokedAt()).isEqualTo(NOW);
    }
}
