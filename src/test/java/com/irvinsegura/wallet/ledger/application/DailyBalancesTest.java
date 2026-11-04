package com.irvinsegura.wallet.ledger.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.irvinsegura.wallet.ledger.DailyBalance;
import com.irvinsegura.wallet.ledger.domain.port.JournalEntryRepository.DayClose;
import com.irvinsegura.wallet.shared.domain.Money;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import org.junit.jupiter.api.Test;

class DailyBalancesTest {

    private static final Currency MXN = Currency.getInstance("MXN");
    private static final LocalDate NOV_1 = LocalDate.of(2026, 11, 1);

    @Test
    void daysWithoutMovementsKeepThePreviousBalance() {
        List<DailyBalance> days = DailyBalances.fill(NOV_1, NOV_1.plusDays(4), 5_000, List.of(
                new DayClose(NOV_1.plusDays(1), 7_500),
                new DayClose(NOV_1.plusDays(3), 1_000)), MXN);

        assertThat(days).extracting(DailyBalance::date)
                .containsExactly(NOV_1, NOV_1.plusDays(1), NOV_1.plusDays(2), NOV_1.plusDays(3), NOV_1.plusDays(4));
        assertThat(days).extracting(day -> day.balance().minorUnits())
                .containsExactly(5_000L, 7_500L, 7_500L, 1_000L, 1_000L);
    }

    @Test
    void aSingleDayClosesWithItsLastMovementOrTheOpeningBalance() {
        assertThat(DailyBalances.fill(NOV_1, NOV_1, 0, List.of(new DayClose(NOV_1, 2_500)), MXN))
                .containsExactly(new DailyBalance(NOV_1, new Money(2_500, MXN)));
        assertThat(DailyBalances.fill(NOV_1, NOV_1, 300, List.of(), MXN))
                .containsExactly(new DailyBalance(NOV_1, new Money(300, MXN)));
    }

    @Test
    void aBalanceDownToZeroIsKept() {
        List<DailyBalance> days = DailyBalances.fill(NOV_1, NOV_1.plusDays(1), 900,
                List.of(new DayClose(NOV_1, 0)), MXN);

        assertThat(days).extracting(day -> day.balance().minorUnits()).containsExactly(0L, 0L);
    }
}
