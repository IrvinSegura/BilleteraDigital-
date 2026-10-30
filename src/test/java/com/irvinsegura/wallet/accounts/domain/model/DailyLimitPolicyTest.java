package com.irvinsegura.wallet.accounts.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.irvinsegura.wallet.accounts.DailyLimitExceededException;
import com.irvinsegura.wallet.shared.domain.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Currency;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DailyLimitPolicyTest {

    private final DailyLimitPolicy policy = new DailyLimitPolicy(
            Map.of(Currency.getInstance("MXN"), mxn(50_000)), ZoneId.of("America/Mexico_City"));

    @Test
    void allowsSpendingUpToTheLimit() {
        assertThatCode(() -> policy.check(mxn(30_000), mxn(20_000))).doesNotThrowAnyException();
    }

    @Test
    void rejectsSpendingBeyondTheLimitAndSaysWhatIsLeft() {
        assertThatThrownBy(() -> policy.check(mxn(30_000), mxn(20_001)))
                .isInstanceOf(DailyLimitExceededException.class)
                .hasMessageContaining("200.00 MXN")
                .extracting("code").isEqualTo("DAILY_LIMIT_EXCEEDED");
    }

    @Test
    void usageAboveALoweredLimitLeavesNothingToSpend() {
        assertThatThrownBy(() -> policy.check(mxn(60_000), mxn(1)))
                .isInstanceOf(DailyLimitExceededException.class)
                .hasMessageContaining("0.00 MXN");
    }

    @Test
    void currenciesWithoutALimitAreNotCapped() {
        assertThatCode(() -> policy.check(Money.ofMinor(0, "USD"), Money.ofMinor(Long.MAX_VALUE / 2, "USD")))
                .doesNotThrowAnyException();
    }

    @Test
    void daysFollowTheConfiguredZone() {
        // 03:00 UTC is still the previous evening in Mexico City
        assertThat(policy.dayOf(Instant.parse("2026-10-30T03:00:00Z"))).isEqualTo(LocalDate.parse("2026-10-29"));
    }

    private static Money mxn(long minorUnits) {
        return Money.ofMinor(minorUnits, "MXN");
    }
}
