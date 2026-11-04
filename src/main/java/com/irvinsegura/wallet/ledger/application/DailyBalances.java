package com.irvinsegura.wallet.ledger.application;

import com.irvinsegura.wallet.ledger.DailyBalance;
import com.irvinsegura.wallet.ledger.domain.port.JournalEntryRepository.DayClose;
import com.irvinsegura.wallet.shared.domain.Money;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Turns the days that had postings into one closing balance per day.
 */
final class DailyBalances {

    private DailyBalances() {
    }

    /**
     * @param openingMinor balance before {@code from}
     * @param closes       closing balance of the days that had postings
     */
    static List<DailyBalance> fill(LocalDate from, LocalDate to, long openingMinor, List<DayClose> closes,
            Currency currency) {
        Map<LocalDate, Long> closingByDay = closes.stream()
                .collect(Collectors.toMap(DayClose::day, DayClose::balanceMinor));
        List<DailyBalance> days = new ArrayList<>();
        long balance = openingMinor;
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            balance = closingByDay.getOrDefault(day, balance);
            days.add(new DailyBalance(day, new Money(balance, currency)));
        }
        return days;
    }
}
