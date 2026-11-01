package com.irvinsegura.wallet.identity.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class OneTimeTokenTest {

    private static final Instant NOW = Instant.parse("2026-10-30T12:00:00Z");

    private final OneTimeToken.Issued issued =
            OneTimeToken.issue(UserId.newId(), TokenPurpose.RESET_PASSWORD, NOW, Duration.ofMinutes(30));

    @Test
    void onlyTheHashIsKept() {
        assertThat(issued.token().tokenHash())
                .isEqualTo(OneTimeToken.hash(issued.rawValue()))
                .isNotEqualTo(issued.rawValue());
    }

    @Test
    void worksOnlyForItsPurposeUntilItExpires() {
        OneTimeToken token = issued.token();

        assertThat(token.isValidFor(TokenPurpose.RESET_PASSWORD, NOW.plus(Duration.ofMinutes(30)).minusMillis(1)))
                .isTrue();
        assertThat(token.isValidFor(TokenPurpose.RESET_PASSWORD, NOW.plus(Duration.ofMinutes(30)))).isFalse();
        assertThat(token.isValidFor(TokenPurpose.VERIFY_EMAIL, NOW)).isFalse();
    }

    @Test
    void policyGivesEachPurposeItsOwnLifetime() {
        OneTimeTokenPolicy policy = new OneTimeTokenPolicy(Duration.ofHours(24), Duration.ofMinutes(30));

        assertThat(policy.timeToLive(TokenPurpose.VERIFY_EMAIL)).isEqualTo(Duration.ofHours(24));
        assertThat(policy.timeToLive(TokenPurpose.RESET_PASSWORD)).isEqualTo(Duration.ofMinutes(30));
    }
}
