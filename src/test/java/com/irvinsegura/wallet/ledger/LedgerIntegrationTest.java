package com.irvinsegura.wallet.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.irvinsegura.wallet.TestcontainersConfiguration;
import com.irvinsegura.wallet.shared.domain.Money;
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
