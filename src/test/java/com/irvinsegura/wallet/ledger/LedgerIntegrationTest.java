package com.irvinsegura.wallet.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.irvinsegura.wallet.TestcontainersConfiguration;
import com.irvinsegura.wallet.shared.domain.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class LedgerIntegrationTest {

    private static final Currency MXN = Currency.getInstance("MXN");

    @Autowired
    private Ledger ledger;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void concurrentSpendingNeverOverdrawsAnAccount() throws Exception {
        UUID alice = fundedAccount(10_000);
        UUID bob = fundedAccount(0);

        // 20 attempts of 10.00 against 100.00: exactly 10 may succeed
        List<Outcome> outcomes = runConcurrently(20, i -> transfer(alice, bob, 1_000));

        assertThat(outcomes).filteredOn(Outcome.SUCCEEDED::equals).hasSize(10);
        assertThat(outcomes).filteredOn(Outcome.INSUFFICIENT_FUNDS::equals).hasSize(10);
        assertThat(ledger.balanceOf(alice)).isEqualTo(mxn(0));
        assertThat(ledger.balanceOf(bob)).isEqualTo(mxn(10_000));
    }

    @Test
    void opposingTransfersDoNotDeadlockAndConserveMoney() throws Exception {
        UUID alice = fundedAccount(5_000);
        UUID bob = fundedAccount(5_000);

        // Without a fixed lock order, A->B and B->A running together lock rows in opposite orders and deadlock
        List<Outcome> outcomes = runConcurrently(60, i -> i % 2 == 0
                ? transfer(alice, bob, 700)
                : transfer(bob, alice, 700));

        assertThat(outcomes).doesNotContain(Outcome.FAILED);
        assertThat(ledger.balanceOf(alice).plus(ledger.balanceOf(bob))).isEqualTo(mxn(10_000));
    }

    @Test
    void statementListsMovementsNewestFirstWithRunningBalance() {
        UUID alice = fundedAccount(10_000);
        UUID bob = fundedAccount(0);
        ledger.record(new NewJournalEntry("TRANSFER", "ref-1", alice, bob, mxn(2_500)));

        MovementPage page = ledger.movementsOf(alice, 0, 1);

        assertThat(page.hasNext()).isTrue();
        AccountMovement latest = page.items().getFirst();
        assertThat(latest.type()).isEqualTo("TRANSFER");
        assertThat(latest.reference()).isEqualTo("ref-1");
        assertThat(latest.direction()).isEqualTo(AccountMovement.Direction.OUT);
        assertThat(latest.amount()).isEqualTo(mxn(2_500));
        assertThat(latest.balanceAfter()).isEqualTo(mxn(7_500));
        assertThat(ledger.movementsOf(alice, 1, 1).items().getFirst().direction())
                .isEqualTo(AccountMovement.Direction.IN);
    }

    @Test
    void flowsAddUpEveryMovementOfThePeriodOnBothSides() {
        Instant start = Instant.now().minusSeconds(60);
        UUID alice = fundedAccount(10_000);
        UUID bob = fundedAccount(0);
        ledger.record(new NewJournalEntry("TRANSFER", null, alice, bob, mxn(2_500)));
        ledger.record(new NewJournalEntry("TRANSFER", null, alice, bob, mxn(1_250)));
        Instant end = Instant.now().plusSeconds(60);

        assertThat(ledger.flowsOf(alice, start, end)).isEqualTo(new FlowTotals(mxn(10_000), mxn(3_750)));
        assertThat(ledger.flowsOf(bob, start, end)).isEqualTo(new FlowTotals(mxn(3_750), mxn(0)));
        // The end is exclusive and a later period has nothing
        assertThat(ledger.flowsOf(alice, end, end.plusSeconds(60))).isEqualTo(new FlowTotals(mxn(0), mxn(0)));
        assertThat(ledger.flowsOf(alice, start.minusSeconds(60), start)).isEqualTo(new FlowTotals(mxn(0), mxn(0)));
    }

    @Test
    void dailyBalancesCloseEachLocalDayWithItsLastMovement() {
        UUID alice = fundedAccount(10_000);
        ledger.record(new NewJournalEntry("TRANSFER", null, alice, fundedAccount(0), mxn(2_500)));

        // Far apart zones put the same instant on different dates; each must count the day in its own zone
        for (ZoneId zone : List.of(ZoneId.of("America/Mexico_City"), ZoneId.of("Pacific/Kiritimati"),
                ZoneId.of("Pacific/Pago_Pago"))) {
            LocalDate today = LocalDate.now(zone);

            List<DailyBalance> days = ledger.dailyBalancesOf(alice, today.minusDays(2), today, zone);

            assertThat(days).extracting(DailyBalance::date)
                    .containsExactly(today.minusDays(2), today.minusDays(1), today);
            assertThat(days).extracting(DailyBalance::balance).containsExactly(mxn(0), mxn(0), mxn(7_500));
            // A day after the movements opens with the balance they left
            assertThat(ledger.dailyBalancesOf(alice, today.plusDays(1), today.plusDays(1), zone))
                    .extracting(DailyBalance::balance).containsExactly(mxn(7_500));
        }
        assertThatThrownBy(() -> ledger.dailyBalancesOf(alice, LocalDate.now(), LocalDate.now().minusDays(1),
                ZoneId.of("UTC"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void journalIsAppendOnly() {
        fundedAccount(100);

        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE postings SET amount_minor = 1"))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM journal_entries"))
                .hasMessageContaining("append-only");
    }

    @Test
    void booksAlwaysBalance() {
        fundedAccount(12_345);

        // Every debit has a matching credit, and the platform's assets equal what it owes users
        Long unbalanced = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM (
                    SELECT currency FROM postings
                    GROUP BY currency
                    HAVING sum(CASE WHEN side = 'DEBIT' THEN amount_minor ELSE -amount_minor END) <> 0) t""",
                Long.class);
        Long mismatched = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM (
                    SELECT currency FROM ledger_accounts
                    GROUP BY currency
                    HAVING sum(CASE WHEN type = 'ASSET' THEN balance_minor ELSE -balance_minor END) <> 0) t""",
                Long.class);

        assertThat(unbalanced).isZero();
        assertThat(mismatched).isZero();
    }

    @Test
    void unsupportedCurrenciesCannotHaveAccounts() {
        assertThatThrownBy(() -> ledger.openUserAccount(Currency.getInstance("EUR")))
                .isInstanceOf(UnsupportedCurrencyException.class);
    }

    private UUID fundedAccount(long minorUnits) {
        UUID account = ledger.openUserAccount(MXN);
        if (minorUnits > 0) {
            ledger.record(new NewJournalEntry("DEPOSIT", null, ledger.settlementAccount(MXN), account,
                    mxn(minorUnits)));
        }
        return account;
    }

    private Outcome transfer(UUID from, UUID to, long minorUnits) {
        try {
            ledger.record(new NewJournalEntry("TRANSFER", null, from, to, mxn(minorUnits)));
            return Outcome.SUCCEEDED;
        } catch (InsufficientFundsException e) {
            return Outcome.INSUFFICIENT_FUNDS;
        } catch (RuntimeException e) {
            return Outcome.FAILED;
        }
    }

    private static List<Outcome> runConcurrently(int tasks, IntTask task) throws Exception {
        try (ExecutorService executor = Executors.newFixedThreadPool(16)) {
            List<Callable<Outcome>> callables = new ArrayList<>();
            for (int i = 0; i < tasks; i++) {
                int index = i;
                callables.add(() -> task.run(index));
            }
            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> future : executor.invokeAll(callables)) {
                outcomes.add(future.get());
            }
            return outcomes;
        }
    }

    private static Money mxn(long minorUnits) {
        return Money.ofMinor(minorUnits, "MXN");
    }

    private enum Outcome {
        SUCCEEDED,
        INSUFFICIENT_FUNDS,
        FAILED
    }

    @FunctionalInterface
    private interface IntTask {
        Outcome run(int index);
    }
}
